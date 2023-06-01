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

package uk.gov.hmrc.personaldetailsvalidation

import akka.Done
import cats.data.EitherT
import cats.implicits.catsStdInstancesForFuture
import com.codahale.metrics.SharedMetricRegistries
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.config.AppConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.personaldetailsvalidation.audit.EventsSender
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.uuid.UUIDProvider

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.{ExecutionContext, Future}

class PersonalDetailsValidatorSpec extends UnitSpec with MockFactory with GuiceOneAppPerSuite {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(Map("metrics.enabled" -> "false")).build()

  "validate" should {

    "match the given personal details with matching service, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails: PersonalDetails = personalDetailsObjects.generateOne

      val gender = "F"
      val personalDetailsWithGender: PersonalDetails = personalDetails.addGender(gender)

      val matchResult: MatchSuccessful = MatchSuccessful(personalDetailsWithGender)

      (matchingConnector.doMatch(_: PersonalDetails)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, *, headerCarrier, executionContext)
        .returning(EitherT.rightT[Future, Exception](matchResult))

      (citizenDetailsConnector.findDesignatoryDetails(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, headerCarrier, executionContext)
        .returning(Future.successful(Some(Gender(gender))))

      (mockAppConfig.returnNinoFromCid _).expects().returning(false).repeat(2)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _: Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, personalDetails, origin, headerCarrier, request, executionContext)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(*, executionContext)
        .returning(EitherT.rightT[Future, Exception](Done))

      await(validator.validate(personalDetails, origin, maybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }
    }

    "match the given postccode personal details with matching service, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in new Setup {

      // SIS-1269

      val inputPersonalDetails: PersonalDetailsWithPostCode = personalDetailsWithPostCodeObjects.generateOne
      val matchedPersonalDetails: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
      val gender = "F"
      val eventPersonalDetails: PersonalDetails = inputPersonalDetails.addNino(matchedPersonalDetails.nino).addGender(gender)
      val matchResult: MatchSuccessful = MatchSuccessful(eventPersonalDetails)

      (matchingConnector.doMatch(_: PersonalDetails)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(inputPersonalDetails, *, headerCarrier, executionContext)
        .returning(EitherT.rightT[Future, Exception](matchResult))

      (citizenDetailsConnector.findDesignatoryDetails(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, headerCarrier, executionContext)
        .returning(Future.successful(Some(Gender(gender))))

      (mockAppConfig.returnNinoFromCid _).expects().returning(true).repeat(1)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, inputPersonalDetails, origin, headerCarrier, request, executionContext)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(*, executionContext)
        .returning(EitherT.rightT[Future, Exception](Done))

      await(validator.validate(inputPersonalDetails, origin, maybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }
    }

    "match the given personal details with matching service, with a different suffix, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails : PersonalDetailsWithNino = personalDetailsObjects.generateOne.asInstanceOf[PersonalDetailsWithNino]

      val enteredNino: Nino = adjustedNino(personalDetails.nino)
      val enteredPersonalDetails: PersonalDetailsWithNino = personalDetails.copy(nino = enteredNino)
      val gender = "F"

      val eventPersonalDetails: PersonalDetails = enteredPersonalDetails.addGender(gender)
      val matchResult: MatchSuccessful = MatchSuccessful(eventPersonalDetails)

      (matchingConnector.doMatch(_: PersonalDetails)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(enteredPersonalDetails, *, headerCarrier, executionContext)
        .returning(EitherT.rightT[Future, Exception](matchResult))

      (citizenDetailsConnector.findDesignatoryDetails(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, headerCarrier, executionContext)
        .returning(Future.successful(Some(Gender(gender))))

      (mockAppConfig.returnNinoFromCid _).expects().returning(false).repeat(2)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, enteredPersonalDetails, origin,  headerCarrier, request, executionContext)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(*, executionContext)
        .returning(EitherT.rightT[Future, Exception](Done))

      await(validator.validate(enteredPersonalDetails, origin, maybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }
    }

    "match the given personal details with matching service, with a different suffix, " +
      "store the returned Nino as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails : PersonalDetailsWithNino = personalDetailsObjects.generateOne.asInstanceOf[PersonalDetailsWithNino]

      val enteredNino: Nino = adjustedNino(personalDetails.nino)
      val enteredPersonalDetails: PersonalDetailsWithNino = personalDetails.copy(nino = enteredNino)
      val gender = "F"

      val matchResult: MatchSuccessful = MatchSuccessful(personalDetails)

      (matchingConnector.doMatch(_: PersonalDetails)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(enteredPersonalDetails, *, headerCarrier, executionContext)
        .returning(EitherT.rightT[Future, Exception](matchResult))

      (citizenDetailsConnector.findDesignatoryDetails(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, headerCarrier, executionContext)
        .returning(Future.successful(Some(Gender(gender))))

      (mockAppConfig.returnNinoFromCid _).expects().returning(true).repeat(2)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(MatchSuccessful(personalDetails.addGender(gender)), personalDetails, origin, headerCarrier, request, executionContext)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(*, executionContext)
        .returning(EitherT.rightT[Future, Exception](Done))

      await(validator.validate(enteredPersonalDetails, origin, maybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }
    }

    "match the given personal details with matching service, " +
      "store them as FailedPersonalDetailsValidation for unsuccessful match " +
      "and return the ValidationId" in new Setup {

      await(personalDetailsValidationRetryRepository.collection.drop().toFuture())
      val personalDetails: PersonalDetails = personalDetailsObjects.generateOne
      val matchResult: MatchFailed = MatchFailed("some error")

      (matchingConnector.doMatch(_: PersonalDetails)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, *, headerCarrier, executionContext)
        .returning(EitherT.rightT[Future, Exception](matchResult))

      (mockAppConfig.returnNinoFromCid _).expects().returning(false)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, personalDetails, origin,  headerCarrier, request, executionContext)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(*, executionContext)
        .returning(EitherT.rightT[Future, Exception](Done))

      await(validator.validate(personalDetails, origin, maybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }
    }

    "return matching error when the call to match fails" in new Setup {
      val personalDetails: PersonalDetails = personalDetailsObjects.generateOne

      val exception = new RuntimeException("error")
      (matchingConnector.doMatch(_: PersonalDetails)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, *, headerCarrier, executionContext)
        .returning(EitherT.leftT[Future, MatchResult](exception))

      (matchingEventsSender.sendErrorEvents(_: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(personalDetails, origin,  headerCarrier, request, executionContext)

      await( validator.validate(personalDetails, origin, maybeCredId).value) shouldBe Left(exception)
    }

    "return matching error when the call to persist fails" in new Setup {
      val personalDetails: PersonalDetails = personalDetailsObjects.generateOne
      val gender = "F"

      val matchResult: MatchSuccessful = MatchSuccessful(personalDetails)

      (matchingConnector.doMatch(_: PersonalDetails)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, *, headerCarrier, executionContext)
        .returning(EitherT.rightT[Future, Exception](matchResult))

      (citizenDetailsConnector.findDesignatoryDetails(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, headerCarrier, executionContext)
        .returning(Future.successful(Some(Gender(gender))))

      (mockAppConfig.returnNinoFromCid _).expects().returning(false)

      val exception = new RuntimeException("error")
      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(*, executionContext)
        .returning(EitherT.leftT[Future, Done](exception))

      (matchingEventsSender.sendErrorEvents(_: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(personalDetails, origin,  headerCarrier, request, executionContext)

      await(validator.validate(personalDetails, origin, maybeCredId).value) shouldBe Left(exception)
    }

    "reset attempts when matching is successful for a given credId" in new Setup {
      val inputPersonalDetails: PersonalDetailsWithPostCode = personalDetailsWithPostCodeObjects.generateOne
      val matchedPersonalDetails: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
      val gender = "F"
      val matchResult: MatchSuccessful = MatchSuccessful(inputPersonalDetails.addNino(matchedPersonalDetails.nino).addGender(gender))

      await(personalDetailsValidationRetryRepository.recordAttempt(maybeCredId.get,3))
      await(personalDetailsValidationRetryRepository.getAttempts(maybeCredId).value) shouldBe Right(4)

      (matchingConnector.doMatch(_: PersonalDetails)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
        .expects(inputPersonalDetails, *, headerCarrier, executionContext)
        .returning(EitherT.rightT[Future, Exception](matchResult))

      (citizenDetailsConnector.findDesignatoryDetails(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, headerCarrier, executionContext)
        .returning(Future.successful(Some(Gender(gender))))

      (mockAppConfig.returnNinoFromCid _).expects().returning(true).repeat(1)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _: Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, inputPersonalDetails, origin, headerCarrier, request, executionContext)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(*, executionContext)
        .returning(EitherT.rightT[Future, Exception](Done))

      await(validator.validate(inputPersonalDetails, origin, maybeCredId).value).map { personalDetailsValidation =>
        personalDetailsValidation.id shouldBe Right(personalDetailsValidation).value.id
      }

      await(personalDetailsValidationRetryRepository.getAttempts(maybeCredId).value) shouldBe Right(0)
    }

  }

  private trait Setup {

    SharedMetricRegistries.clear()

    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    val matchingConnector: MatchingConnector = mock[MatchingConnector]
    val citizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
    val matchingEventsSender: EventsSender = mock[EventsSender]
    val mockAppConfig: AppConfig = mock[AppConfig]

    val repository: PdvRepository = mock[PdvRepository]

    val personalDetailsValidationMongoRepositoryConfig: PersonalDetailsValidationMongoRepositoryConfig =
      app.injector.instanceOf[PersonalDetailsValidationMongoRepositoryConfig]

    val mongoComponent: MongoComponent = app.injector.instanceOf[MongoComponent]

    val personalDetailsValidationRetryRepository: PersonalDetailsValidationRetryRepository =
      new PersonalDetailsValidationRetryRepository(personalDetailsValidationMongoRepositoryConfig, mongoComponent)

    implicit val uuidProvider: UUIDProvider = stub[UUIDProvider]
    (uuidProvider.apply _).expects().returning(randomUUID)

    def adjustedNino(nino: Nino) : Nino = {
      val ninoPrefix = nino.nino.substring(0, 8)
      val ninoSuffix = nino.nino.charAt(8)

      val newSuffix : Char = chooseOneOf("ABCD".toList.filter(_ != ninoSuffix)).generateOne

      Nino(s"$ninoPrefix$newSuffix")
    }
    val origin: Some[String] = Some("test")
    val maybeCredId: Some[String] = Some("credentialId")
    val validator = new PersonalDetailsValidatorImpl(matchingConnector, citizenDetailsConnector, repository, personalDetailsValidationRetryRepository, matchingEventsSender, mockAppConfig)
  }
}
