/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json.{Format, JsValue, Json, Reads, __}
import uk.gov.hmrc.domain._
import uk.gov.hmrc.http._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CitizenDetailsConnector @Inject()(http: CoreGet, val config: CitizenDetailsConnectorConfig) extends Logging {

  lazy val cdBaseUrl = config.baseUrl

  def findDesignatoryDetails(nino: Nino) (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Gender]] = {
    val url = s"$cdBaseUrl/$nino/designatory-details"
    http.GET[Option[Gender]](url).recover(toNone(url))
  }

  private def toNone[T](url: String): PartialFunction[Throwable, Option[T]] = {
    case exception =>
      logger.error(s"Call to GET $url threw: $exception")
      None
  }
}

case class Gender(gender: String)

object Gender {

  implicit val reads: Reads[Option[Gender]] = (__ \ "person" \ "sex").readNullable[String].map(gender => gender.map(Gender(_)))

}