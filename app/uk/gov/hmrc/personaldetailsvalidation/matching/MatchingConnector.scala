/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.personaldetailsvalidation.matching

import cats.data.EitherT
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.http.Status._
import play.api.libs.json.JsObject
import uk.gov.hmrc.http._
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector._
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@ImplementedBy(classOf[FuturedMatchingConnector])
trait MatchingConnector[Interpretation[_]] {

  def doMatch(personalDetails: PersonalDetails)
             (implicit headerCarrier: HeaderCarrier, executionContext: ExecutionContext): EitherT[Interpretation, Exception, MatchResult]

}

@Singleton
class FuturedMatchingConnector @Inject()(httpClient: HttpClient, connectorConfig: MatchingConnectorConfig) extends MatchingConnector[Future] {

  import connectorConfig.authenticatorBaseUrl
  import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsFormat._

  def doMatch(personalDetails: PersonalDetails)
             (implicit headerCarrier: HeaderCarrier,
              executionContext: ExecutionContext): EitherT[Future, Exception, MatchResult] =
    EitherT(httpClient.POST[JsObject, Either[Exception, MatchResult]](
      url = s"$authenticatorBaseUrl/match",
      body = personalDetails.toJson
    ) recover {
      case ex: Exception => Left(ex)
    })

  private implicit val matchingResultHttpReads: HttpReads[Either[Exception, MatchResult]] = new HttpReads[Either[Exception, MatchResult]] {
    override def read(method: String, url: String, response: HttpResponse): Either[Exception, MatchResult] = response.status match {
      case OK => Right(MatchSuccessful(response.json.as[PersonalDetails]))
      case UNAUTHORIZED => Right(MatchFailed((response.json \ "errors").as[String]))
      case other => Left(new BadGatewayException(s"Unexpected response from $method $url with status: '$other' and body: ${response.body}"))
    }
  }
}

object MatchingConnector {

  sealed trait MatchResult

  object MatchResult {

    case class MatchSuccessful(matchedPerson: PersonalDetails) extends MatchResult

    case class MatchFailed(errors: String) extends MatchResult

  }

}
