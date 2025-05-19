/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.personaldetailsvalidation

import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.{JsError, JsSuccess, Reads, __}
import uk.gov.hmrc.domain._
import uk.gov.hmrc.http._
import javax.inject.Inject
import uk.gov.hmrc.config.AppConfig

import scala.concurrent.{ExecutionContext, Future}

class CitizenDetailsConnector @Inject()(http: CoreGet, val config: CitizenDetailsConnectorConfig,
                                        val appConfig: AppConfig) extends Logging {

  lazy val cdBaseUrl = config.baseUrl

  def findDesignatoryDetails(nino: Nino) (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Gender]] = {
    if (appConfig.cidDesignatoryDetailsCallEnabled) {
      val url = s"$cdBaseUrl/$nino/designatory-details"
      http.GET[Option[Gender]](url).recover(toNone(url, nino))
    } else {
      Future.successful(None)
    }
  }

  private def toNone[T](url: String, nino: Nino): PartialFunction[Throwable, Option[T]] = {
    case exception =>

      val maskedNino: String = s"${nino.value.take(1)}XXXXX${nino.value.takeRight(3)}"

      logger.error(s"Call to GET ${url.replace(nino.value, maskedNino)} threw: ${exception.toString.replace(nino.value, maskedNino)}")
      None
  }

  private implicit def httpGenderReads: HttpReads[Option[Gender]] = new HttpReads[Option[Gender]] {

    override def read(method: String, url: String, response: HttpResponse): Option[Gender] = {

      def maskNino(url: String, s: String): String = {

        val nino: String = url.replace("/designatory-details","").takeRight(9)

        val maskedNino: String = s"${nino.take(1)}XXXXX${nino.takeRight(3)}"

        s.replace(nino, maskedNino)
      }

      response.status match {
        case OK => (response.json \ "person" \ "sex").validateOpt[String] match {
          case JsSuccess(value, _) => value.map(Gender(_))
          case JsError(_) =>
            logger.error(s"Call to GET ${maskNino(url, url)} returned invalid value for gender")
            None
        }
        case NOT_FOUND =>
          logger.warn(s"Call to GET ${maskNino(url, url)} returned not found")
          None
        case LOCKED =>
          logger.warn(s"Call to GET ${maskNino(url, url)} returned locked")
          None
        case unexpectedStatus =>
          logger.error(s"Call to GET ${maskNino(url, url)} returned status $unexpectedStatus and body ${maskNino(url, response.body)}")
          None
      }

    }

  }

}

case class Gender(gender: String)

object Gender {

  implicit val reads: Reads[Option[Gender]] = (__ \ "person" \ "sex").readNullable[String].map(gender => gender.map(Gender(_)))

}