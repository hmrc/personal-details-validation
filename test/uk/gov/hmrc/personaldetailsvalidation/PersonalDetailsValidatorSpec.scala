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

package uk.gov.hmrc.personaldetailsvalidation

import java.util.UUID.randomUUID

import akka.Done
import cats.Id
import cats.data.EitherT
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.MatchingEventsSender
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, PersonalDetailsValidation}
import uk.gov.hmrc.play.test.UnitSpec
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


      (matchingEventsSender.sendMatchResultEvent(_: MatchResult)(_: HeaderCarrier, _: ExecutionContext))
        .expects(matchResult, headerCarrier, executionContext)

      (matchingEventsSender.sendSuffixMatchingEvent(_: PersonalDetails, _: MatchResult)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, matchResult, headerCarrier, executionContext)

      val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(EitherT.rightT[Id, Exception](Done))

      validator.validate(personalDetails).value shouldBe Right(personalDetailsValidation.id)
    }

    "match the given personal details with matching service, " +
      "store them as FailedPersonalDetailsValidation for unsuccessful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne
      val matchResult = MatchFailed

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.rightT[Id, Exception](matchResult))

      (matchingEventsSender.sendMatchResultEvent(_: MatchResult)(_: HeaderCarrier, _: ExecutionContext))
        .expects(matchResult, headerCarrier, executionContext)

      (matchingEventsSender.sendSuffixMatchingEvent(_: PersonalDetails, _: MatchResult)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, matchResult, headerCarrier, executionContext)

      val personalDetailsValidation = PersonalDetailsValidation.failed()

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(EitherT.rightT[Id, Exception](Done))

      validator.validate(personalDetails).value shouldBe Right(personalDetailsValidation.id)
    }

    "return matching error when the call to match fails" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne

      val exception = new RuntimeException("error")
      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.leftT[Id, MatchResult](exception))

      (matchingEventsSender.sendMatchingErrorEvent(_: HeaderCarrier, _: ExecutionContext))
        .expects(headerCarrier, executionContext)

      validator.validate(personalDetails).value shouldBe Left(exception)
    }

    "return matching error when the call to persist fails" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne

      val matchResult = MatchSuccessful(personalDetails)

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.rightT[Id, Exception](matchResult))

      val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)

      val exception = new RuntimeException("error")
      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(EitherT.leftT[Id, Done](exception))

      (matchingEventsSender.sendMatchingErrorEvent(_: HeaderCarrier, _: ExecutionContext))
        .expects(headerCarrier, executionContext)

      validator.validate(personalDetails).value shouldBe Left(exception)
    }
  }

  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val matchingConnector = mock[MatchingConnector[Id]]
    val matchingEventsSender = mock[MatchingEventsSender]

    val repository = mock[PersonalDetailsValidationRepository[Id]]
    implicit val uuidProvider: UUIDProvider = stub[UUIDProvider]
    uuidProvider.apply _ when() returns randomUUID()

    val validator = new PersonalDetailsValidator(matchingConnector, repository, matchingEventsSender)
  }
}
