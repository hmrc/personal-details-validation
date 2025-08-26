/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.http.Status._
import uk.gov.hmrc.circuitbreaker._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsFormat._
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult._
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector._
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.net.URI
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MatchingConnector @Inject()(httpClient: HttpClientV2,
                                  connectorConfig: MatchingConnectorConfig,
                                  auditDataFactory: AuditDataEventFactory,
                                  auditConnector: AuditConnector) extends UsingCircuitBreaker {

  def doMatch(personalDetails: PersonalDetails)
             (implicit headerCarrier: HeaderCarrier, executionContext: ExecutionContext): EitherT[Future, Exception, MatchResult] = {

    val uri = URI.create(s"${connectorConfig.authenticatorBaseUrl}/match").toURL

    EitherT(
      withCircuitBreaker {
        httpClient
          .post(uri)(headerCarrier)
          .withBody(personalDetails.toJson)
          .execute[Either[Exception, MatchResult]]
      } recover {
        case ex: UnhealthyServiceException =>
          auditConnector.sendEvent(auditDataFactory.createCircuitBreakerEvent(personalDetails))
          Left(ex)
        case ex: Exception =>
          Left(ex)
      }
    )
  }

  private implicit val matchingResultHttpReads: HttpReads[Either[Exception, MatchResult]] = new HttpReads[Either[Exception, MatchResult]] {
    override def read(method: String, url: String, response: HttpResponse): Either[Exception, MatchResult] = response.status match {
      case OK                => Right(MatchSuccessful(response.json.as[PersonalDetails]))
      case FAILED_DEPENDENCY => Right(NoLivingMatch)
      case UNAUTHORIZED      => Right(MatchFailed((response.json \ "errors").as[String]))
      case other             => throw new BadGatewayException(s"Unexpected response from $method $url with status: '$other' and body: ${response.body}")
    }
  }

  override protected def circuitBreakerConfig: CircuitBreakerConfig = {
    CircuitBreakerConfig(
      this.getClass.getSimpleName,
      connectorConfig.circuitBreakerNumberOfCallsToTrigger,
      connectorConfig.circuitBreakerUnavailableDuration,
      connectorConfig.circuitBreakerUnstableDuration
    )
  }

  override def breakOnException(t: Throwable): Boolean = t match {
    case (_: NotFoundException | _: BadRequestException) => false
    case _ => true
  }
}

object MatchingConnector {

  sealed trait MatchResult

  object MatchResult {

    case class MatchSuccessful(matchedPerson: PersonalDetails) extends MatchResult

    case class MatchFailed(errors: String) extends MatchResult

    case object NoLivingMatch extends MatchResult

  }

}
