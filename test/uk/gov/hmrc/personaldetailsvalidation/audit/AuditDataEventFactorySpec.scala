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

package uk.gov.hmrc.personaldetailsvalidation.audit

import java.time.LocalDate

import generators.Generators.Implicits._
import generators.Generators.nonEmptyMap
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import play.api.Configuration
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory._
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model._

class AuditDataEventFactorySpec extends UnitSpec with MockFactory {

  "factory" should {

    val personalDetails: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
    val personalDetailsWithPostCode: PersonalDetailsWithPostCode = personalDetailsWithPostCodeObjects.generateOne

    val matchingResultAndDetails = Map(
      MatchSuccessful(personalDetails) -> Map("matchingStatus" -> "success"),
      MatchFailed("some errors") -> Map("matchingStatus" -> "failed", "failureDetail" -> "some errors")
    )

    matchingResultAndDetails.foreach { case (matchResult, matchingingDetails) =>
      s"create data event for ${matchResult.getClass.getName.split("\\$").last}" in new Setup {
        val dataEvent = auditDataFactory.createEvent(matchResult, personalDetails)

        dataEvent.auditSource shouldBe auditConfig.appName
        dataEvent.auditType shouldBe "MatchingResult"
        dataEvent.tags shouldBe auditTags
        dataEvent.detail shouldBe auditDetails +
          ("nino" -> personalDetails.nino.value) +
          ("postCode" -> "NOT SUPPLIED") +
          ("age" -> currentAgeFromDateOfBirth(personalDetails.dateOfBirth)) ++
          matchingingDetails
      }
    }

    "create data event for a failed match of postCode" in new Setup {
      val matchResult = MatchFailed("Some Error")
      val dataEvent = auditDataFactory.createEvent(matchResult, personalDetailsWithPostCode)

      dataEvent.auditSource shouldBe auditConfig.appName
      dataEvent.auditType shouldBe "MatchingResult"
      dataEvent.tags shouldBe auditTags
      dataEvent.detail shouldBe auditDetails +
        ("nino" -> "NOT SUPPLIED") +
        ("postCode" -> personalDetailsWithPostCode.postCode.value) +
        ("age" -> currentAgeFromDateOfBirth(personalDetailsWithPostCode.dateOfBirth)) +
        ("matchingStatus" -> "failed") +
        ("failureDetail" -> "Some Error")
    }

    "create error data event" in new Setup {
      val dataEvent = auditDataFactory.createErrorEvent(personalDetails)

      dataEvent.auditSource shouldBe auditConfig.appName
      dataEvent.auditType shouldBe "MatchingResult"
      dataEvent.tags shouldBe auditTags
      dataEvent.detail shouldBe auditDetails +
        ("nino" -> personalDetails.nino.value) +
        ("postCode" -> "NOT SUPPLIED") +
        ("age" -> currentAgeFromDateOfBirth(personalDetails.dateOfBirth)) +
        ("matchingStatus" -> "technicalError")
    }

    "create error data event for user without nino" in new Setup {
      val adjustedPerson = new PersonalDetailsWithPostCode(personalDetails.firstName, personalDetails.lastName, personalDetails.dateOfBirth, postCode = "SE1 9NT")
      val dataEvent = auditDataFactory.createErrorEvent(adjustedPerson)

      dataEvent.auditSource shouldBe auditConfig.appName
      dataEvent.auditType shouldBe "MatchingResult"
      dataEvent.tags shouldBe auditTags
      dataEvent.detail shouldBe auditDetails +
        ("nino" -> "NOT SUPPLIED") +
        ("postCode" -> "SE1 9NT") +
        ("age" -> currentAgeFromDateOfBirth(adjustedPerson.dateOfBirth)) +
        ("matchingStatus" -> "technicalError")
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

    def currentAgeFromDateOfBirth(dateOfBirth: LocalDate): String = {
      if (LocalDate.now.getDayOfYear >= dateOfBirth.getDayOfYear - 1) (LocalDate.now.getYear - dateOfBirth.getYear).toString
      else (LocalDate.now.getYear - dateOfBirth.getYear - 1).toString
    }
  }

}
