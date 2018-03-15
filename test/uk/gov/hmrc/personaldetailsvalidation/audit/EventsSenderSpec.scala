/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.audit.{GAEvent, PlatformAnalyticsConnector}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}

class EventsSenderSpec extends UnitSpec with MockFactory with ScalaFutures {

  "sendEvents" should {
    "send success MatchResultEvent and suffix event when nino suffix matches between external person and matched person" in new Setup {

      (platformAnalyticsConnector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success"), headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_nino_suffix_same_as_cid"), headerCarrier, executionContext)

      val matchResult = MatchSuccessful(personalDetails)

      (auditDataEventFactory.createEvent(_: MatchResult, _: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(matchResult, personalDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendEvents(matchResult, personalDetails)
    }

    "send success MatchResultEvent and suffix event when nino suffix does not match between external person and matched person" in new Setup {
      val matchedPersonDetails = personalDetails.copy(nino = Nino("AA000003C"))

      (platformAnalyticsConnector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success"), headerCarrier, executionContext)

      (platformAnalyticsConnector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_nino_suffix_different_from_cid"), headerCarrier, executionContext)

      val matchResult = MatchSuccessful(matchedPersonDetails)

      (auditDataEventFactory.createEvent(_: MatchResult, _: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(matchResult, personalDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendEvents(matchResult, personalDetails)
    }

    "send failure MatchResultEvent" in new Setup {

      (platformAnalyticsConnector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "failed_matching"), headerCarrier, executionContext)

      val matchResult : MatchResult = MatchFailed("some errors")

      (auditDataEventFactory.createEvent(_: MatchResult, _: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(matchResult, personalDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendEvents(matchResult, personalDetails)
    }
  }

  "sendErrorEvents" should {

    "send technical error matching event" in new Setup {

      (platformAnalyticsConnector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "technical_error_matching"), headerCarrier, executionContext)

      (auditDataEventFactory.createErrorEvent(_: PersonalDetails)(_: HeaderCarrier, _: Request[_]))
        .expects(personalDetails, headerCarrier, request)
        .returning(dataEvent)

      (auditConnector.sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(dataEvent, headerCarrier, executionContext)

      sender.sendErrorEvents(personalDetails)
    }
  }

  trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val request = FakeRequest()
    val generatedPerson = personalDetailsWithNinoObjects.generateOne
    val personalDetails = generatedPerson.copy(nino = Nino("AA000003D"))
    val dataEvent = dataEvents.generateOne

    val platformAnalyticsConnector = mock[PlatformAnalyticsConnector]
    val auditConnector = mock[AuditConnector]
    val auditDataEventFactory = mock[AuditDataEventFactory]
    val sender = new EventsSender(platformAnalyticsConnector, auditConnector, auditDataEventFactory)
  }

}
