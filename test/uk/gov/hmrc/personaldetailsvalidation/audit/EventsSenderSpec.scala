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

package uk.gov.hmrc.personaldetailsvalidation.audit

import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import play.api.mvc.Request
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.audit.{GAEvent, PlatformAnalyticsConnector}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}

class EventsSenderSpec extends UnitSpec with MockFactory with ScalaFutures {

  "sendEvents" should {
    "send success MatchResultEvent and suffix event when nino suffix matches between external person and matched person" in new Setup {

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success"), origin, Some(personalDetails), *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_withNINO"), origin, Some(personalDetails), *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_nino_suffix_same_as_cid"), origin, Some(personalDetails), *, headerCarrier, executionContext)

      val matchResult: MatchSuccessful = MatchSuccessful(personalDetails)

      (auditDataEventFactory.createEvent(_: MatchResult, _: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(matchResult, personalDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendEvents(matchResult, personalDetails, origin)
    }

    "send success MatchResultEvent and suffix event when nino suffix matches between external person and matched person with gender" in new Setup {

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success"), origin, Some(personalDetailsWithGender), *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_withNINO"), origin, Some(personalDetailsWithGender), *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_nino_suffix_same_as_cid"), origin, Some(personalDetailsWithGender), *, headerCarrier, executionContext)

      val matchResult: MatchSuccessful = MatchSuccessful(personalDetailsWithGender)

      (auditDataEventFactory.createEvent(_: MatchResult, _: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(matchResult, personalDetailsWithGender, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendEvents(matchResult, personalDetailsWithGender, origin)
    }

    "send success MatchResultEvent and suffix event when nino suffix does not match between external person and matched person" in new Setup {
      val matchedPersonDetails: PersonalDetailsWithNino = personalDetails.copy(nino = Nino("AA000003C"))

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success"), origin, Some(matchedPersonDetails), *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_withNINO"), origin, Some(matchedPersonDetails), *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_nino_suffix_different_from_cid"), origin, Some(matchedPersonDetails), *, headerCarrier, executionContext)

      val matchResult: MatchSuccessful = MatchSuccessful(matchedPersonDetails)

      (auditDataEventFactory.createEvent(_: MatchResult, _: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(matchResult, personalDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendEvents(matchResult, personalDetails, origin)
    }

    "send success MatchResultEvent and suffix event when postcode matches between external person and matched person" in new Setup {
      val matchedPersonDetails: PersonalDetailsWithPostCode = PersonalDetailsWithPostCode(personalDetails.firstName, personalDetails.lastName, personalDetails.dateOfBirth, "SE1 9NT")

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success"), origin, Some(personalDetails), *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_withPOSTCODE"), origin, Some(personalDetails), *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_postcode_suffix"), origin, Some(personalDetails), *, headerCarrier, executionContext)

      val matchResult: MatchSuccessful = MatchSuccessful(personalDetails)

      (auditDataEventFactory.createEvent(_: MatchResult, _: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(matchResult, matchedPersonDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendEvents(matchResult, matchedPersonDetails, origin)
    }

    "send failure MatchResultEvent" in new Setup {

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "failed_matching"), origin, None, *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "failed_matching_withNINO"), origin, None, *, headerCarrier, executionContext)

      val matchResult : MatchResult = MatchFailed("some errors")

      (auditDataEventFactory.createEvent(_: MatchResult, _: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(matchResult, personalDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendEvents(matchResult, personalDetails, origin)
    }

    "send failure MatchResultEvent with postcode" in new Setup {
      val matchedPersonDetails: PersonalDetailsWithPostCode =
        PersonalDetailsWithPostCode(personalDetails.firstName, personalDetails.lastName, personalDetails.dateOfBirth, "SE1 9NT")

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "failed_matching"), origin, None, *, headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "failed_matching_withPOSTCODE"), origin, None, *, headerCarrier, executionContext)

      val matchResult : MatchResult = MatchFailed("some errors")

      (auditDataEventFactory.createEvent(_: MatchResult, _: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(matchResult, matchedPersonDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendEvents(matchResult, matchedPersonDetails, origin)
    }
  }

  "sendErrorEvents" should {

    "send technical error matching event" in new Setup {

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _: Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "technical_error_matching"), origin, None, *, headerCarrier, executionContext)

      (auditDataEventFactory.createErrorEvent(_: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(personalDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendErrorEvents(personalDetails, origin)
    }
  }

  "sentCircuitBreakerEvent" should {

    "send sentCircuitBreakerEvent when authenticator is down" in new Setup {

      override val dataEvent: DataEvent = DataEvent(
        auditSource = "personal-details-validation",
        auditType = "CircuitBreakerUnhealthyService",
        detail = Map("unavailableServiceName" -> "authenticator") ++ Map("unconfirmedNino" -> "AA000003D")
      )

      (platformAnalyticsConnector.sendEvent(_: GAEvent, _ : Option[String], _: Option[PersonalDetails])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "circuit_breaker", "pdv_unavailable_circuit-breaker"), None, Some(personalDetails), *, *, *)

      (auditDataEventFactory.createCircuitBreakerEvent(_: PersonalDetails))
        .expects(personalDetails).returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sentCircuitBreakerEvent(personalDetails)
    }
  }

  trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val request: Request[_] = FakeRequest()
    val generatedPersonWithGender: PersonalDetailsWithNinoAndGender = personalDetailsWithNinoGenderObjects.generateOne
    val personalDetailsWithGender: PersonalDetailsWithNinoAndGender = generatedPersonWithGender.copy(nino = Nino("AA000003D"))
    val generatedPerson: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
    val personalDetails: PersonalDetailsWithNino = generatedPerson.copy(nino = Nino("AA000003D"))
    val dataEvent: DataEvent = dataEvents.generateOne

    val platformAnalyticsConnector: PlatformAnalyticsConnector = mock[PlatformAnalyticsConnector]
    val auditConnector: AuditConnector = mock[AuditConnector]
    val auditDataEventFactory: AuditDataEventFactory = mock[AuditDataEventFactory]
    val sender = new EventsSender(platformAnalyticsConnector, auditConnector, auditDataEventFactory)
    val origin: Option[String] = Some("test")
  }

}
