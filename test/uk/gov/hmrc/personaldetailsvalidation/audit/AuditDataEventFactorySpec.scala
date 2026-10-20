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

package uk.gov.hmrc.personaldetailsvalidation.audit

import generators.Generators.Implicits.*
import generators.Generators.nonEmptyMap
import generators.ObjectGenerators.*
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory.*
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchNinoLocked, MatchPreconditionFailed, MatchSuccessful, NoLivingMatch}
import uk.gov.hmrc.personaldetailsvalidation.model.*
import com.mongodb.ServerAddress
import com.mongodb.{MongoWriteException, WriteError}
import org.bson.BsonDocument
import uk.gov.hmrc.play.audit.model.DataEvent

import java.time.{LocalDate, LocalDateTime}

class AuditDataEventFactorySpec extends UnitSpec {

  "factory" should {

    val personalDetails: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
    val personalDetailsWithPostCode: PersonalDetailsWithPostCode = personalDetailsWithPostCodeObjects.generateOne

    val matchingResultAndDetails = Map(
      MatchSuccessful(personalDetails) -> Map("matchingStatus" -> "success"),
      MatchFailed("some errors") -> Map("matchingStatus" -> "failed", "failureDetail" -> "some errors"),
      NoLivingMatch -> Map("matchingStatus" -> "success")
    )

    matchingResultAndDetails.foreach { case (matchResult, matchingDetails) =>
      s"create data event for ${matchResult.getClass.getName.split("\\$").last}" in new Setup {
        val dataEvent: DataEvent = auditDataFactory.createEvent(matchResult, personalDetails)

        dataEvent.auditSource shouldBe auditConfig.appName
        dataEvent.auditType shouldBe "MatchingResult"
        dataEvent.tags shouldBe auditTags
        dataEvent.detail shouldBe auditDetails +
          ("nino" -> personalDetails.nino.value) +
          ("postCode" -> "NOT SUPPLIED") +
          ("age" -> currentAgeFromDateOfBirth(personalDetails.dateOfBirth)) ++
          matchingDetails
      }
    }

    val matchingResultAndDetailsForPrecondition = Map(
      MatchPreconditionFailed("underage") -> Map("matchingStatus" -> "failed", "failureDetail" -> "underage")
    )

    matchingResultAndDetailsForPrecondition.foreach { case (matchResult, matchingDetails) =>
      s"create precondition failed data event for ${matchResult.getClass.getName.split("\\$").last}" in new Setup {
        val dataEvent: DataEvent = auditDataFactory.createEvent(matchResult, personalDetails)

        dataEvent.auditSource shouldBe auditConfig.appName
        dataEvent.auditType shouldBe "MatchingPreconditionFailedUnderage"
        dataEvent.tags shouldBe auditTags
        dataEvent.detail shouldBe auditDetails +
          ("nino" -> personalDetails.nino.value) +
          ("postCode" -> "NOT SUPPLIED") +
          ("dateOfBirth" -> personalDetails.dateOfBirth.toString) +
          ("age" -> currentAgeFromDateOfBirth(personalDetails.dateOfBirth)) ++
          matchingDetails
      }
    }

    "create data event for a failed match of postCode" in new Setup {
      val matchResult: MatchFailed = MatchFailed("Some Error")
      val dataEvent: DataEvent = auditDataFactory.createEvent(matchResult, personalDetailsWithPostCode)

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
      val dataEvent: DataEvent = auditDataFactory.createErrorEvent(personalDetails)

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
      val adjustedPerson = PersonalDetailsWithPostCode(personalDetails.firstName, personalDetails.lastName, personalDetails.dateOfBirth, postCode = "SE1 9NT")
      val dataEvent: DataEvent = auditDataFactory.createErrorEvent(adjustedPerson)

      dataEvent.auditSource shouldBe auditConfig.appName
      dataEvent.auditType shouldBe "MatchingResult"
      dataEvent.tags shouldBe auditTags
      dataEvent.detail shouldBe auditDetails +
        ("nino" -> "NOT SUPPLIED") +
        ("postCode" -> "SE1 9NT") +
        ("age" -> currentAgeFromDateOfBirth(adjustedPerson.dateOfBirth)) +
        ("matchingStatus" -> "technicalError")
    }

    "create data event for a blocked nino submission" in new Setup {
      val dataEvent: DataEvent = auditDataFactory.createEvent(MatchNinoLocked, personalDetails)

      dataEvent.auditSource shouldBe auditConfig.appName
      dataEvent.auditType shouldBe "MatchingResult"
      dataEvent.tags shouldBe auditTags
      dataEvent.detail shouldBe auditDetails +
        ("nino" -> personalDetails.nino.value) +
        ("postCode" -> "NOT SUPPLIED") +
        ("age" -> currentAgeFromDateOfBirth(personalDetails.dateOfBirth)) +
        ("matchingStatus" -> "failed") +
        ("failureDetail" -> "Nino submission blocked: nino locked")
    }

    "create a NinoLockEvent recording a failure" in new Setup {
      val dataEvent: DataEvent = auditDataFactory.createNinoLockEvent(personalDetails, Some("cred-123"), NinoLockOutcome.FailureRecorded(failedAttempts = 2))

      dataEvent.auditSource shouldBe auditConfig.appName
      dataEvent.auditType shouldBe "NinoLockEvent"
      dataEvent.tags shouldBe auditTags
      dataEvent.detail shouldBe auditDetails +
        ("outcome" -> "failureRecorded") +
        ("failedAttempts" -> "2") +
        ("nino" -> personalDetails.nino.value) +
        ("credId" -> "cred-123")
    }

    "create a NinoLockEvent recording a failure for a submission without a credId" in new Setup {
      val dataEvent: DataEvent = auditDataFactory.createNinoLockEvent(personalDetails, None, NinoLockOutcome.FailureRecorded(failedAttempts = 1))

      dataEvent.auditType shouldBe "NinoLockEvent"
      dataEvent.detail shouldBe auditDetails +
        ("outcome" -> "failureRecorded") +
        ("failedAttempts" -> "1") +
        ("nino" -> personalDetails.nino.value) +
        ("credId" -> "NOT SUPPLIED")
    }

    "create a NinoLockEvent for a created lock" in new Setup {
      val lockedUntil: LocalDateTime = LocalDateTime.of(2026, 1, 2, 12, 0)
      val dataEvent: DataEvent = auditDataFactory.createNinoLockEvent(personalDetails, Some("cred-123"), NinoLockOutcome.LockCreated(lockedUntil))

      dataEvent.auditType shouldBe "NinoLockEvent"
      dataEvent.detail shouldBe auditDetails +
        ("outcome" -> "lockCreated") +
        ("lockedUntil" -> lockedUntil.toString) +
        ("nino" -> personalDetails.nino.value) +
        ("credId" -> "cred-123")
    }

    "create a NinoLockEvent for a blocked submission" in new Setup {
      val dataEvent: DataEvent = auditDataFactory.createNinoLockEvent(personalDetails, Some("cred-123"), NinoLockOutcome.SubmissionBlocked)

      dataEvent.auditType shouldBe "NinoLockEvent"
      dataEvent.detail shouldBe auditDetails +
        ("outcome" -> "submissionBlocked") +
        ("nino" -> personalDetails.nino.value) +
        ("credId" -> "cred-123")
    }

    "create a NinoLockEvent for a blocked submission without a credId" in new Setup {
      val dataEvent: DataEvent = auditDataFactory.createNinoLockEvent(personalDetails, None, NinoLockOutcome.SubmissionBlocked)

      dataEvent.auditType shouldBe "NinoLockEvent"
      dataEvent.detail shouldBe auditDetails +
        ("outcome" -> "submissionBlocked") +
        ("nino" -> personalDetails.nino.value) +
        ("credId" -> "NOT SUPPLIED")
    }

    "create a NinoLockEvent for a lock store error" in new Setup {
      val dataEvent: DataEvent = auditDataFactory.createNinoLockEvent(personalDetails, Some("cred-123"), NinoLockOutcome.LockStoreError("read", "MongoTimeoutException", "timed out after 30000 ms"))

      dataEvent.auditType shouldBe "NinoLockEvent"
      dataEvent.detail shouldBe auditDetails +
        ("outcome" -> "lockStoreError") +
        ("operation" -> "read") +
        ("errorType" -> "MongoTimeoutException") +
        ("errorMessage" -> "timed out after 30000 ms") +
        ("nino" -> personalDetails.nino.value) +
        ("credId" -> "cred-123")
    }

    "include the full error message when a real duplicate key error is produced" in new Setup {
      val realDupKeyMessage =
        "E11000 duplicate key error collection: pdv.personal-details-validation-nino-lock-store index: ninoKeyUnique dup key: { ninoKey: \"AA000003\" }"
      val ex = new MongoWriteException(new WriteError(11000, realDupKeyMessage, new BsonDocument()), new ServerAddress())
      val dataEvent: DataEvent = auditDataFactory.createNinoLockEvent(personalDetails, Some("cred-123"), NinoLockOutcome.LockStoreError("write", ex.getClass.getSimpleName, ex.getMessage))

      dataEvent.auditType shouldBe "NinoLockEvent"
      dataEvent.detail("errorType") shouldBe "MongoWriteException"
      dataEvent.detail("errorMessage") should include(realDupKeyMessage)
    }

    "create a NinoLockEvent with a NOT SUPPLIED nino for personal details without a nino" in new Setup {
      val dataEvent: DataEvent = auditDataFactory.createNinoLockEvent(personalDetailsWithPostCode, Some("cred-123"), NinoLockOutcome.FailureRecorded(failedAttempts = 1))

      dataEvent.auditType shouldBe "NinoLockEvent"
      dataEvent.detail shouldBe auditDetails +
        ("outcome" -> "failureRecorded") +
        ("failedAttempts" -> "1") +
        ("nino" -> "NOT SUPPLIED") +
        ("credId" -> "cred-123")
    }
  }

  trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    val auditTagsProvider: AuditTagProvider = mock[AuditTagProvider]
    val auditDetailsProvider: AuditDetailsProvider = mock[AuditDetailsProvider]
    val auditConfig: AuditConfig = new AuditConfig(mock[Configuration]) {
      override lazy val appName: String = "personal-details-validation"
    }

    val auditTags: Map[String, String] = nonEmptyMap.generateOne
    val auditDetails: Map[String, String] = nonEmptyMap.generateOne

    when(auditTagsProvider.apply(headerCarrier, matchingResultAuditType, request)).thenReturn(auditTags)
    when(auditTagsProvider.apply(headerCarrier, matchingPreconditionFailedUnderageAuditType, request)).thenReturn(auditTags)
    when(auditTagsProvider.apply(headerCarrier, ninoLockEventAuditType, request)).thenReturn(auditTags)
    when(auditDetailsProvider.apply(headerCarrier)).thenReturn(auditDetails)

    val auditDataFactory = new AuditDataEventFactory(auditConfig, auditTagsProvider, auditDetailsProvider)

    def currentAgeFromDateOfBirth(dateOfBirth: LocalDate): String = {
      if (LocalDate.now.getDayOfYear >= dateOfBirth.getDayOfYear - 1) (LocalDate.now.getYear - dateOfBirth.getYear).toString
      else (LocalDate.now.getYear - dateOfBirth.getYear - 1).toString
    }
  }

}
