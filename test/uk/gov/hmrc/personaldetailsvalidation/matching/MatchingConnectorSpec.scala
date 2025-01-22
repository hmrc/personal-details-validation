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

package uk.gov.hmrc.personaldetailsvalidation.matching

import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import play.api.Configuration
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import setups.HttpClientStubSetup
import support.UnitSpec
import uk.gov.hmrc.circuitbreaker.UnhealthyServiceException
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier}
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory.{AuditDetailsProvider, AuditTagProvider}
import uk.gov.hmrc.personaldetailsvalidation.audit.{AuditConfig, AuditDataEventFactory}
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, PersonalDetailsWithNino}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MatchingConnectorSpec
  extends UnitSpec
    with ScalaFutures
    with MockFactory {

  "doMatch" should {

    "return MatchSuccessful when POST to authenticator's /authenticator/match returns OK" in new Setup {

      val matchingResponsePayload: JsObject = payload + ("nino" -> JsString(ninoWithDifferentSuffix.value))

      expectPost(toUrl = "http://host/authenticator/match")
        .withPayload(payload)
        .returning(OK, matchingResponsePayload)

      connector.doMatch(personalDetails)
        .value.futureValue shouldBe Right(MatchSuccessful(personalDetails.copy(nino = ninoWithDifferentSuffix)))
    }

    "return MatchFailed with errors when POST to authenticator's /authenticator/match returns UNAUTHORISED" in new Setup {
      val errors = "Last Name does not match CID"
      expectPost(toUrl = "http://host/authenticator/match")
        .withPayload(payload)
        .returning(UNAUTHORIZED, Json.obj("errors" -> errors))

      connector.doMatch(personalDetails).value.futureValue shouldBe Right(MatchFailed(errors))
    }

    Set(NO_CONTENT, NOT_FOUND, INTERNAL_SERVER_ERROR) foreach { unexpectedStatus =>

      s"return Left when POST to /authenticator/match returns $unexpectedStatus" in new Setup {

        expectPost(toUrl = "http://host/authenticator/match")
          .withPayload(payload)
          .returning(unexpectedStatus, "some response body")

        val Left(expectedException) = connector.doMatch(personalDetails).value.futureValue
        expectedException shouldBe a[BadGatewayException]
        expectedException.getMessage shouldBe s"Unexpected response from POST http://host/authenticator/match with status: '$unexpectedStatus' and body: some response body"
      }
    }

    "return Left when authenticator went down" in new Setup {

      val exception = new UnhealthyServiceException("some error")

      expectPost(toUrl = "http://host/authenticator/match").withPayload(payload).throwing(exception)
      (mockAuditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext)).expects(*, *, *)
      connector.doMatch(personalDetails).value.futureValue shouldBe Left(exception)
    }

    "return Left when POST to /authenticator/match returns a failed Future" in new Setup {

      val exception = new RuntimeException("some error")

      expectPost(toUrl = "http://host/authenticator/match")
        .withPayload(payload)
        .throwing(exception)

      connector.doMatch(personalDetails).value.futureValue shouldBe Left(exception)
    }
  }

  private trait Setup extends HttpClientStubSetup {

    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val request: Request[_] = FakeRequest()

    val nino: Nino = Nino("AA000003D")
    val ninoWithDifferentSuffix: Nino = Nino("AA000003C")
    val generatedPersonalDetails: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
    val personalDetails: PersonalDetailsWithNino = generatedPersonalDetails.copy(nino = nino)
    val payload: JsObject = Json.obj(
      "firstName" -> personalDetails.firstName,
      "lastName" -> personalDetails.lastName,
      "dateOfBirth" -> personalDetails.dateOfBirth,
      "nino" -> personalDetails.nino
    )

    private val connectorConfig = new MatchingConnectorConfig(mock[HostConfigProvider]) {
      override lazy val authenticatorBaseUrl = "http://host/authenticator"
      override def circuitBreakerNumberOfCallsToTrigger: Int   = 20
      override def circuitBreakerUnavailableDuration: Int = 60
      override def circuitBreakerUnstableDuration: Int    = 300
    }

    val auditTagsProvider: AuditTagProvider = mock[AuditTagProvider]
    val auditDetailsProvider: AuditDetailsProvider = mock[AuditDetailsProvider]
    val auditConfig: AuditConfig = new AuditConfig(mock[Configuration]) {
      override lazy val appName: String = "personal-details-validation"
    }
    val mockAuditDataEventFactory = mock[AuditDataEventFactory]
    val auditDataFactory = new AuditDataEventFactory(auditConfig, auditTagsProvider, auditDetailsProvider)
    val mockAuditConnector = mock[AuditConnector]

    val connector = new MatchingConnectorImpl(httpClient, connectorConfig, auditDataFactory, mockAuditConnector)

    implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5 seconds, interval = 100 millis)
  }
}
