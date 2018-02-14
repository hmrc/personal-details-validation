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

import generators.ObjectGenerators.personalDetailsObjects
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.audit.{GAEvent, PlatformAnalyticsConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import generators.Generators.Implicits._
import uk.gov.hmrc.domain.Nino

class MatchingEventsSenderSpec extends UnitSpec with MockFactory with ScalaFutures {

  "sendEvents" should {
    "send success MatchResultEvent and suffix event when nino suffix matches between external person and matched person" in new Setup {

      (connector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success"), headerCarrier, executionContext)

      (connector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_nino_suffix_same_as_cid"), headerCarrier, executionContext)


      sender.sendEvents(MatchSuccessful(personalDetails), personalDetails)
    }

    "send success MatchResultEvent and suffix event when nino suffix does not match between external person and matched person" in new Setup {
      val matchedPersonDetails = personalDetails.copy(nino = Nino("AA000003C"))

      (connector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success"), headerCarrier, executionContext)

      (connector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "success_nino_suffix_different_from_cid"), headerCarrier, executionContext)

      sender.sendEvents(MatchSuccessful(matchedPersonDetails), personalDetails)
    }

    "send failure MatchResultEvent" in new Setup {

      (connector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "failed_matching"), headerCarrier, executionContext)

      sender.sendEvents(MatchFailed, personalDetails)
    }

    "send technical error matching event" in new Setup {

      (connector.sendEvent(_: GAEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(GAEvent("sos_iv", "personal_detail_validation_result", "technical_error_matching"), headerCarrier, executionContext)

      sender.sendMatchingErrorEvent
    }
  }

  trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    val personalDetails = personalDetailsObjects.generateOne.copy(nino = Nino("AA000003D"))

    val connector = mock[PlatformAnalyticsConnector]
    val sender = new MatchingEventsSender(connector)
  }

}
