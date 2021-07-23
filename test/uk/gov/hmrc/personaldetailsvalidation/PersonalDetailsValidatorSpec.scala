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

package uk.gov.hmrc.personaldetailsvalidation

import java.util.UUID.randomUUID

import akka.Done
import cats.Id
import cats.data.EitherT
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import play.api.test.FakeRequest
import support.UnitSpec
import uk.gov.hmrc.config.AppConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.EventsSender
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, PersonalDetailsValidation, PersonalDetailsWithNino}
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}

class PersonalDetailsValidatorSpec
  extends UnitSpec
    with MockFactory {

  "validate" should {

    "match the given personal details with matching service, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne

      val matchResult = MatchSuccessful(personalDetails)

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.rightT[Id, Exception](matchResult))

      (mockAppConfig.returnNinoFromCid _).expects().returning(false).repeat(2)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, personalDetails, origin, headerCarrier, request, executionContext)

      (matchingEventsSender.sendBeginEvent(_ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(origin, headerCarrier, request, executionContext)

      val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(EitherT.rightT[Id, Exception](Done))

      validator.validate(personalDetails, origin).value shouldBe Right(personalDetailsValidation)
    }

    "match the given postccode personal details with matching service, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in new Setup {

      // SIS-1269

      val inputPersonalDetails = personalDetailsWithPostCodeObjects.generateOne
      val matchedPersonalDetails = personalDetailsWithNinoObjects.generateOne
      val matchResult = MatchSuccessful(matchedPersonalDetails)

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(inputPersonalDetails, headerCarrier, executionContext)
        .returning(EitherT.rightT[Id, Exception](matchResult))

      (mockAppConfig.returnNinoFromCid _).expects().returning(true).repeat(1)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, inputPersonalDetails, origin, headerCarrier, request, executionContext)

      (matchingEventsSender.sendBeginEvent(_ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(origin, headerCarrier, request, executionContext)

      val personalDetailsValidation = PersonalDetailsValidation.successful(inputPersonalDetails.addNino(matchedPersonalDetails.nino))

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(EitherT.rightT[Id, Exception](Done))

      validator.validate(inputPersonalDetails, origin).value shouldBe Right(personalDetailsValidation)
    }

    "match the given personal details with matching service, with a different suffix, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails : PersonalDetailsWithNino = (personalDetailsObjects.generateOne).asInstanceOf[PersonalDetailsWithNino]

      val enteredNino = adjustedNino(personalDetails.nino)
      val enteredPersonalDetails = personalDetails.copy(nino = enteredNino)

      val matchResult = MatchSuccessful(personalDetails)

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(enteredPersonalDetails, headerCarrier, executionContext)
        .returning(EitherT.rightT[Id, Exception](matchResult))

      (mockAppConfig.returnNinoFromCid _).expects().returning(false).repeat(2)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, enteredPersonalDetails, origin,  headerCarrier, request, executionContext)

      (matchingEventsSender.sendBeginEvent(_ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(origin, headerCarrier, request, executionContext)

      val personalDetailsValidation = PersonalDetailsValidation.successful(enteredPersonalDetails)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(EitherT.rightT[Id, Exception](Done))

      validator.validate(enteredPersonalDetails, origin).value shouldBe Right(personalDetailsValidation)
    }

    "match the given personal details with matching service, with a different suffix, " +
      "store the returned Nino as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails : PersonalDetailsWithNino = (personalDetailsObjects.generateOne).asInstanceOf[PersonalDetailsWithNino]

      val enteredNino = adjustedNino(personalDetails.nino)
      val enteredPersonalDetails = personalDetails.copy(nino = enteredNino)

      val matchResult = MatchSuccessful(personalDetails)

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(enteredPersonalDetails, headerCarrier, executionContext)
        .returning(EitherT.rightT[Id, Exception](matchResult))

      (mockAppConfig.returnNinoFromCid _).expects().returning(true).repeat(2)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, personalDetails, origin, headerCarrier, request, executionContext)

      (matchingEventsSender.sendBeginEvent(_ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(origin, headerCarrier, request, executionContext)

      val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(EitherT.rightT[Id, Exception](Done))

      validator.validate(enteredPersonalDetails, origin).value shouldBe Right(personalDetailsValidation)
    }

    "match the given personal details with matching service, " +
      "store them as FailedPersonalDetailsValidation for unsuccessful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne
      val matchResult = MatchFailed("some error")

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.rightT[Id, Exception](matchResult))

      (mockAppConfig.returnNinoFromCid _).expects().returning(false)

      (matchingEventsSender.sendEvents(_: MatchResult, _: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(matchResult, personalDetails, origin,  headerCarrier, request, executionContext)

      (matchingEventsSender.sendBeginEvent(_ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(origin, headerCarrier, request, executionContext)

      val personalDetailsValidation = PersonalDetailsValidation.failed()

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(EitherT.rightT[Id, Exception](Done))

      validator.validate(personalDetails, origin).value shouldBe Right(personalDetailsValidation)
    }

    "return matching error when the call to match fails" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne

      val exception = new RuntimeException("error")
      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.leftT[Id, MatchResult](exception))

      (matchingEventsSender.sendErrorEvents(_: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(personalDetails, origin,  headerCarrier, request, executionContext)

      (matchingEventsSender.sendBeginEvent(_ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(origin, headerCarrier, request, executionContext)

      validator.validate(personalDetails, origin).value shouldBe Left(exception)
    }

    "return matching error when the call to persist fails" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne

      val matchResult = MatchSuccessful(personalDetails)

      (matchingEventsSender.sendBeginEvent(_ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(origin, headerCarrier, request, executionContext)

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.rightT[Id, Exception](matchResult))

      (mockAppConfig.returnNinoFromCid _).expects().returning(false)

      val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)

      val exception = new RuntimeException("error")
      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(EitherT.leftT[Id, Done](exception))

      (matchingEventsSender.sendErrorEvents(_: PersonalDetails, _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
        .expects(personalDetails, origin,  headerCarrier, request, executionContext)

      validator.validate(personalDetails, origin).value shouldBe Left(exception)
    }
  }

  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    implicit val request = FakeRequest()

    val matchingConnector = mock[MatchingConnector[Id]]
    val matchingEventsSender = mock[EventsSender]
    val mockAppConfig = mock[AppConfig]

    val repository = mock[PersonalDetailsValidationRepository[Id]]
    implicit val uuidProvider: UUIDProvider = stub[UUIDProvider]
    uuidProvider.apply _ when() returns randomUUID()

    def adjustedNino(nino: Nino) : Nino = {
      val ninoPrefix = nino.nino.substring(0, 8)
      val ninoSuffix = nino.nino.charAt(8)

      val newSuffix : Char = chooseOneOf("ABCD".toList.filter(_ != ninoSuffix)).generateOne

      Nino(s"$ninoPrefix$newSuffix")
    }
    val origin = Some("test")

    val validator = new PersonalDetailsValidator(matchingConnector, repository, matchingEventsSender, mockAppConfig)
  }
}
