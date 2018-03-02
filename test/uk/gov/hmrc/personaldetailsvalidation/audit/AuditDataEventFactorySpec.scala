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
import generators.Generators.nonEmptyMap
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import play.api.Configuration
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory._
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.play.test.UnitSpec

class AuditDataEventFactorySpec extends UnitSpec with MockFactory {

  "factory" should {

    val personalDetails = personalDetailsObjects.generateOne

    val matchResults = Map(MatchSuccessful(personalDetails) -> "success", MatchFailed("some errors") -> "failed")

    matchResults.foreach { case (matchResult, matchingStatus) =>
      s"create data event for ${matchResult.getClass.getName.split("\\$").last}" in new Setup {
        val dataEvent = auditDataFactory.createEvent(matchResult, personalDetails)

        dataEvent.auditSource shouldBe auditConfig.appName
        dataEvent.auditType shouldBe "MatchingResult"
        dataEvent.tags shouldBe auditTags
        dataEvent.detail shouldBe auditDetails + ("nino" -> personalDetails.nino.value) + ("matchingStatus" -> matchingStatus)
      }
    }

    "create error data event" in new Setup {
      val dataEvent = auditDataFactory.createErrorEvent(personalDetails)

      dataEvent.auditSource shouldBe auditConfig.appName
      dataEvent.auditType shouldBe "MatchingResult"
      dataEvent.tags shouldBe auditTags
      dataEvent.detail shouldBe auditDetails + ("nino" -> personalDetails.nino.value) + ("matchingStatus" -> "technicalError")
    }

  }

  trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val request = FakeRequest()

    val auditTagsProvider = mock[AuditTagProvider]
    val auditDetailsProvider = mock[AuditDetailsProvider]
    val auditConfig = new AuditConfig(mock[Configuration]) {
      override lazy val appName: String = "personal-details-validation"
    }

    val auditTags = nonEmptyMap.generateOne
    val auditDetails = nonEmptyMap.generateOne

    auditTagsProvider.apply _ expects(headerCarrier, auditType, request) returns auditTags
    auditDetailsProvider.apply _ expects (headerCarrier) returns auditDetails

    val auditDataFactory = new AuditDataEventFactory(auditConfig, auditTagsProvider, auditDetailsProvider)

  }

}
