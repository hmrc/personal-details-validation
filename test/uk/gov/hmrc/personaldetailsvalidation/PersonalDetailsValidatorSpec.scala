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

package uk.gov.hmrc.personaldetailsvalidation

import cats.data.EitherT
import com.codahale.metrics.SharedMetricRegistries
import generators.Generators.Implicits.*
import generators.ObjectGenerators.*
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, verifyNoInteractions, when}
import org.mongodb.scala.SingleObservableFuture
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Request
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchNinoLocked, MatchPreconditionFailed, MatchPreconditionSuccessful, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.mocks.audits.{MockAuditDataFactory, MockAuditEventConnector}
import uk.gov.hmrc.personaldetailsvalidation.mocks.connectors.{MockCitizensDetailsConnector, MockMatchingConnector}
import uk.gov.hmrc.personaldetailsvalidation.mocks.services.MockRepoControlService
import uk.gov.hmrc.personaldetailsvalidation.model.*
import uk.gov.hmrc.personaldetailsvalidation.services.{Encryption, NinoRetryLockService}
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.uuid.UUIDProvider

import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


class PersonalDetailsValidatorSpec extends
  UnitSpec
  with CommonTestData
  with GuiceOneAppPerSuite
  with BeforeAndAfterEach {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(Map("metrics.enabled" -> "false")).build()

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  SharedMetricRegistries.clear()

  val gender = "F"

  implicit val encryption: Encryption = app.injector.instanceOf[Encryption]
  val mongoComponent: MongoComponent = app.injector.instanceOf[MongoComponent]

  val dataEvent: DataEvent = dataEvents.generateOne

  val nino: Nino = personalDetailsWithNinoObjects.generateOne.nino

  val personalDetailsValidationMongoRepositoryConfig: PersonalDetailsValidationMongoRepositoryConfig =
    app.injector.instanceOf[PersonalDetailsValidationMongoRepositoryConfig]

  val personalDetailsValidationRetryRepository: PersonalDetailsValidationRetryRepository =
    new PersonalDetailsValidationRetryRepository(personalDetailsValidationMongoRepositoryConfig, mongoComponent)

  implicit val uuidProvider: UUIDProvider = new UUIDProvider()

  def adjustedNino(nino: Nino): Nino = {
    val ninoPrefix = nino.nino.substring(0, 8)
    val ninoSuffix = nino.nino.charAt(8)

    val newSuffix: Char = chooseOneOf("ABCD".toList.filter(_ != ninoSuffix)).generateOne

    Nino(s"$ninoPrefix$newSuffix")
  }

  def stubReturnNinoFromCid(value: Boolean, times: Int): Unit = {
    val values = Seq.fill(times)(value)
    when(mockAppConfig.returnNinoFromCid).thenReturn(values.head, values.tail*)
  }

  def stubReturnNinoFromCidFalse(value: Boolean): Unit = {
    when(mockAppConfig.returnNinoFromCid).thenReturn(value)
  }
  
  val validator = new PersonalDetailsValidatorImpl(
    mockMatchingConnector,
    mockCitizenDetailsConnector,
    mockRepoControlService,
    personalDetailsValidationRetryRepository,
    mockNinoRetryLockService,
    mockAuditDataFactory,
    mockMatchingAuditConnector,
    mockAppConfig
  )

  override def beforeEach(): Unit = {
    reset(mockMatchingConnector,
      mockCitizenDetailsConnector,
      mockMatchingAuditConnector,
      mockRepoControlService,
      mockNinoRetryLockService)
    await(personalDetailsValidationRetryRepository.collection.drop().toFuture())
    when(mockNinoRetryLockService.lockStatus(any[PersonalDetails], any[Option[String]])(using any[HeaderCarrier], any[Request[?]]))
      .thenReturn(EitherT.rightT[Future, Exception](NinoRetryLockService.NinoLockStatus.NotLocked))
    when(mockNinoRetryLockService.recordOutcome(any[PersonalDetails], any[MatchResult], any[Option[String]])(using any[HeaderCarrier], any[Request[?]]))
      .thenReturn(EitherT.rightT[Future, Exception](()))
    super.beforeEach()
  }

  "validate" should {

    "match the given personal details with matching service, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in {
      val personalDetailsWithGender: PersonalDetails = personalDetails.addGender(gender)
      val matchResult: MatchSuccessful = MatchSuccessful(personalDetailsWithGender)

      MockMatchingConnector.doMatch(mockMatchingConnector, personalDetails)(matchResult)
      MockCitizensDetailsConnector.findDesignatoryDetails(mockCitizenDetailsConnector)(Some(Gender(gender)))

      stubReturnNinoFromCid(value = false, times = 2)

      MockAuditDataFactory.createEvent(matchResult, personalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)

      MockRepoControlService.insertPDVAndAssociationRecord(
        mockRepoControlService, testMaybeCredId)(Done)

      await(validator.validate(personalDetails, testOrigin, testMaybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }
    }

    "match the given postcode personal details with matching service, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in {

      val inputPersonalDetails: PersonalDetailsWithPostCode = personalDetailsWithPostCodeObjects.generateOne
      val matchedPersonalDetails: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
      
      val eventPersonalDetails: PersonalDetails = inputPersonalDetails.addNino(matchedPersonalDetails.nino).addGender(gender)
      val matchResult: MatchSuccessful          = MatchSuccessful(eventPersonalDetails)

      MockMatchingConnector.doMatch(mockMatchingConnector, inputPersonalDetails)(matchResult)
      MockCitizensDetailsConnector.findDesignatoryDetails(mockCitizenDetailsConnector)(Some(Gender(gender)))

      stubReturnNinoFromCid(value = true, times = 1)

      MockAuditDataFactory.createEvent(matchResult, inputPersonalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)
      MockRepoControlService.insertPDVAndAssociationRecord(
        mockRepoControlService, testMaybeCredId)(Done)

      await(validator.validate(inputPersonalDetails, testOrigin, testMaybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }
    }

    "match the given personal details with matching service, with a different suffix, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in {
      val personalDetails: PersonalDetailsWithNino =
        personalDetailsObjects.generateOne.asInstanceOf[PersonalDetailsWithNino]
      val enteredNino: Nino = adjustedNino(personalDetails.nino)
      val enteredPersonalDetails: PersonalDetailsWithNino = personalDetails.copy(nino = enteredNino)

      val eventPersonalDetails: PersonalDetails = enteredPersonalDetails.addGender(gender)
      val matchResult: MatchSuccessful = MatchSuccessful(eventPersonalDetails)

      MockMatchingConnector.doMatch(mockMatchingConnector, enteredPersonalDetails)(matchResult)
      MockCitizensDetailsConnector.findDesignatoryDetails(mockCitizenDetailsConnector)(Some(Gender(gender)))

      stubReturnNinoFromCid(value = false, times = 2)

      MockAuditDataFactory.createEvent(matchResult, enteredPersonalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)
      MockRepoControlService.insertPDVAndAssociationRecord(
        mockRepoControlService, testMaybeCredId)(Done)

      await(validator.validate(enteredPersonalDetails, testOrigin, testMaybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }
    }

    "match the given personal details with matching service, with a different suffix, " +
      "store the returned Nino as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in {
      val personalDetails: PersonalDetailsWithNino        = personalDetailsObjects.generateOne.asInstanceOf[PersonalDetailsWithNino]
      val enteredNino: Nino                               = adjustedNino(personalDetails.nino)
      val enteredPersonalDetails: PersonalDetailsWithNino = personalDetails.copy(nino = enteredNino)

      val matchResult: MatchSuccessful           = MatchSuccessful(personalDetails)
      val matchResultWithGender: MatchSuccessful = MatchSuccessful(personalDetails.addGender(gender))

      MockMatchingConnector.doMatch(mockMatchingConnector, enteredPersonalDetails)(matchResult)
      MockCitizensDetailsConnector.findDesignatoryDetails(mockCitizenDetailsConnector)(Some(Gender(gender)))

      stubReturnNinoFromCid(value = true, times = 2)

      MockAuditDataFactory.createEvent(matchResultWithGender, personalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)
      MockRepoControlService.insertPDVAndAssociationRecord(
        mockRepoControlService, testMaybeCredId)(Done)

      await(validator.validate(enteredPersonalDetails, testOrigin, testMaybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
        personalDetailsValidation shouldBe a [SuccessfulPersonalDetailsValidation]
      }
    }

    "do not match the given personal details with matching service, " +
      "store them as FailedPersonalDetailsValidation for unsuccessful precondition check " +
      "and return the ValidationId" in {

      val matchResult: MatchPreconditionFailed = MatchPreconditionFailed("underage")

      val personalDetailsUnderage =
        personalDetailsWithPostCodeObjects.generateOne.copy(dateOfBirth = LocalDate.now().minusYears(1))

      MockAuditDataFactory.createEvent(matchResult, personalDetailsUnderage)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)
      MockRepoControlService.insertPDVAndAssociationRecord(
        mockRepoControlService, testMaybeCredId)(Done)

      await(validator.validate(personalDetailsUnderage, testOrigin, testMaybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
        personalDetailsValidation shouldBe a [FailedPersonalDetailsValidation]
      }
    }

    "match the given personal details with matching service, " +
      "store them as FailedPersonalDetailsValidation for unsuccessful match " +
      "and return the ValidationId" in {

      val matchResult: MatchFailed = MatchFailed("some error")

      MockMatchingConnector.doMatch(mockMatchingConnector, personalDetails)(matchResult)
      stubReturnNinoFromCidFalse(value = false)

      MockAuditDataFactory.createEvent(matchResult, personalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)
      MockRepoControlService.insertPDVAndAssociationRecord(
        mockRepoControlService, testMaybeCredId)(Done)

      await(validator.validate(personalDetails, testOrigin, testMaybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
        personalDetailsValidation shouldBe a [FailedPersonalDetailsValidation]
      }
    }

    "return matching error when the call to match fails" in {
      MockMatchingConnector.doMatchError(mockMatchingConnector, personalDetails)(exception)
      MockAuditDataFactory.createErrorEvent(personalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)

      await(validator.validate(personalDetails, testOrigin, testMaybeCredId).value) shouldBe Left(exception)
    }

    "return matching error when the call to persist fails" in {
      val matchResult: MatchSuccessful = MatchSuccessful(personalDetails)

      MockMatchingConnector.doMatch(mockMatchingConnector, personalDetails)(matchResult)
      MockCitizensDetailsConnector.findDesignatoryDetails(mockCitizenDetailsConnector)(Some(Gender(gender)))

      stubReturnNinoFromCidFalse(value = false)

      MockRepoControlService.insertPDVAndAssociationRecordError(
        mockRepoControlService, testMaybeCredId)(exception)

      MockAuditDataFactory.createErrorEvent(personalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)

      await(validator.validate(personalDetails, testOrigin, testMaybeCredId).value) shouldBe Left(exception)
    }

    "reset attempts when matching is successful for a given credId" in {
      val inputPersonalDetails: PersonalDetailsWithPostCode = personalDetailsWithPostCodeObjects.generateOne
      val matchedPersonalDetails: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
      val matchResult: MatchSuccessful =
        MatchSuccessful(inputPersonalDetails.addNino(matchedPersonalDetails.nino).addGender(gender))

      await(personalDetailsValidationRetryRepository.incrementAttempts(testMaybeCredId.get))
      await(personalDetailsValidationRetryRepository.incrementAttempts(testMaybeCredId.get))
      await(personalDetailsValidationRetryRepository.incrementAttempts(testMaybeCredId.get))
      await(personalDetailsValidationRetryRepository.getAttemptsT(testMaybeCredId).value) shouldBe Right(3)

      MockMatchingConnector.doMatch(mockMatchingConnector, inputPersonalDetails)(matchResult)
      MockCitizensDetailsConnector.findDesignatoryDetails(mockCitizenDetailsConnector)(Some(Gender(gender)))

      stubReturnNinoFromCid(value = true, times = 1)

      MockAuditDataFactory.createErrorEvent(inputPersonalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)

      MockRepoControlService.insertPDVAndAssociationRecord(
        mockRepoControlService, testMaybeCredId)(Done)

      await(validator.validate(inputPersonalDetails, testOrigin, testMaybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }

      val result: EitherT[Future, Exception, Int] = personalDetailsValidationRetryRepository.getAttemptsT(testMaybeCredId)

      await(result.value) match {
        case Right(attempts) => attempts shouldBe 0
        case Left(exception) => fail(s"Expected Right(0) but got Left($exception)")
      }

    }

    "return an ordinary failed validation without calling authenticator or citizen details when the nino is locked" in {
      val ninoDetails: PersonalDetailsWithNino = PersonalDetailsWithNino("Jim", "Ferguson", LocalDate.of(1980, 1, 1), nino)

      when(mockNinoRetryLockService.lockStatus(eqTo(ninoDetails), eqTo(testMaybeCredId))(using any[HeaderCarrier], any[Request[?]]))
        .thenReturn(EitherT.rightT[Future, Exception](NinoRetryLockService.NinoLockStatus.Locked(LocalDateTime.now(ZoneOffset.UTC).plusHours(1))))

      MockAuditDataFactory.createEvent(MatchNinoLocked, ninoDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)
      MockRepoControlService.insertPDVAndAssociationRecord(
        mockRepoControlService, testMaybeCredId)(Done)

      val result = await(validator.validate(ninoDetails, testOrigin, testMaybeCredId).value)

      result match {
        case Right(failure: FailedPersonalDetailsValidation) => failure.attempt shouldBe Some(1)
        case other => fail(s"Expected Right(FailedPersonalDetailsValidation) but got $other")
      }

      verifyNoInteractions(mockMatchingConnector)
      verifyNoInteractions(mockCitizenDetailsConnector)
    }

    "fail closed without calling authenticator when the nino lock store cannot be read" in {
      when(mockNinoRetryLockService.lockStatus(any[PersonalDetails], any[Option[String]])(using any[HeaderCarrier], any[Request[?]]))
        .thenReturn(EitherT.leftT[Future, NinoRetryLockService.NinoLockStatus](exception))

      MockAuditDataFactory.createErrorEvent(personalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)

      await(validator.validate(personalDetails, testOrigin, testMaybeCredId).value) shouldBe Left(exception)

      verifyNoInteractions(mockMatchingConnector)
    }

    "fail closed when recording the nino lock outcome fails" in {
      when(mockNinoRetryLockService.recordOutcome(any[PersonalDetails], any[MatchResult], any[Option[String]])(using any[HeaderCarrier], any[Request[?]]))
        .thenReturn(EitherT.leftT[Future, Unit](exception))

      MockMatchingConnector.doMatch(mockMatchingConnector, personalDetails)(MatchFailed("some error"))
      stubReturnNinoFromCidFalse(value = false)
      MockAuditDataFactory.createErrorEvent(personalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)

      await(validator.validate(personalDetails, testOrigin, testMaybeCredId).value) shouldBe Left(exception)
    }

  }

  "matchPreconditionCheck" should {
    val fifteenYearsNineMonthsInMonths = 189
    "return MatchPreconditionFailed when personal details contain and DOB less than 15 years 9 months ago" in {
      val matchResult = MatchPreconditionFailed(s"The supplied details have an age younger than 15 years 9 months")
      val personalDetailsUnderage =
        personalDetailsWithPostCodeObjects.generateOne.copy(dateOfBirth = LocalDate.now().minusMonths(fifteenYearsNineMonthsInMonths).plusDays(1))

      await(validator.matchPreconditionCheck(personalDetailsUnderage).value) shouldBe Right(matchResult)
    }
    "1 return MatchPreconditionSuccessful when personal details contain and DOB 15 years 9 months ago" in {
      val personalDetailsUnderage =
        personalDetailsWithPostCodeObjects.generateOne.copy(dateOfBirth = LocalDate.now().minusMonths(fifteenYearsNineMonthsInMonths))

      await(validator.matchPreconditionCheck(personalDetailsUnderage).value) shouldBe Right(MatchPreconditionSuccessful)
    }
    "2 return MatchPreconditionSuccessful when personal details contain and DOB more than 15 years 9 months ago" in {
      val personalDetailsUnderage =
        personalDetailsWithPostCodeObjects.generateOne.copy(dateOfBirth = LocalDate.now().minusMonths(fifteenYearsNineMonthsInMonths).minusDays(1))

      await(validator.matchPreconditionCheck(personalDetailsUnderage).value) shouldBe Right(MatchPreconditionSuccessful)
    }
  }

}
