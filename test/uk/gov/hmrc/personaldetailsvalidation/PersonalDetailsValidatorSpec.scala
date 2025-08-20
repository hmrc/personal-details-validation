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

import com.codahale.metrics.SharedMetricRegistries
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.apache.pekko.Done
import org.mockito.MockitoSugar.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.mocks.audits.{MockAuditDataFactory, MockAuditEventConnector}
import uk.gov.hmrc.personaldetailsvalidation.mocks.connectors.{MockCitizensDetailsConnector, MockMatchingConnector}
import uk.gov.hmrc.personaldetailsvalidation.mocks.services.MockRepoControlService
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.personaldetailsvalidation.services.Encryption
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}

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
    when(mockAppConfig.returnNinoFromCid).thenReturn(values.head, values.tail: _*)
  }

  def stubReturnNinoFromCidFalse(value: Boolean): Unit = {
    when(mockAppConfig.returnNinoFromCid).thenReturn(value)
  }
  
  val validator = new PersonalDetailsValidatorImpl(
    mockMatchingConnector,
    mockCitizenDetailsConnector,
    mockRepoControlService,
    personalDetailsValidationRetryRepository,
    mockAuditDataFactory,
    mockMatchingAuditConnector,
    mockAppConfig
  )

  override def beforeEach(): Unit = {
    reset(mockMatchingConnector,
      mockCitizenDetailsConnector,
      mockMatchingAuditConnector,
      mockRepoControlService)
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
      }
    }

    "match the given personal details with matching service, " +
      "store them as FailedPersonalDetailsValidation for unsuccessful match " +
      "and return the ValidationId" in {

      await(personalDetailsValidationRetryRepository.collection.drop().toFuture())
      val matchResult: MatchFailed = MatchFailed("some error")

      MockMatchingConnector.doMatch(mockMatchingConnector, personalDetails)(matchResult)
      stubReturnNinoFromCidFalse(value = false)

      MockAuditDataFactory.createEvent(matchResult, personalDetails)(dataEvent)
      MockAuditEventConnector.sendEvent(dataEvent)(AuditResult.Success)
      MockRepoControlService.insertPDVAndAssociationRecord(
        mockRepoControlService, testMaybeCredId)(Done)

      await(validator.validate(personalDetails, testOrigin, testMaybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
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

      await(personalDetailsValidationRetryRepository.recordAttempt(testMaybeCredId.get, 3))
      await(personalDetailsValidationRetryRepository.getAttempts(testMaybeCredId).value) shouldBe Right(4)

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

      await(personalDetailsValidationRetryRepository.getAttempts(testMaybeCredId).value) shouldBe Right(0)
    }

  }
}
