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
import cats.data.EitherT
import cats.implicits._
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.MatchingEventsSender
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.{MatchResult, MatchingError}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, PersonalDetailsValidation}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.{ExecutionContext, Future}

class PersonalDetailsValidatorSpec
  extends UnitSpec
    with ScalaFutures
    with MockFactory {

  "validate" should {

    "match the given personal details with matching service, " +
      "store them as SuccessfulPersonalDetailsValidation for successful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne

      val matchResult = MatchSuccessful

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.right[MatchingError](Future.successful(matchResult)))


      (matchingEventsSender.sendMatchResultEvent(_: MatchResult)(_: HeaderCarrier, _: ExecutionContext))
        .expects(matchResult, headerCarrier, executionContext)
        .returning(Future.successful(Done))

      val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(Future.successful(Done))

      validator.validate(personalDetails).value.futureValue shouldBe Right(personalDetailsValidation.id)
    }

    "match the given personal details with matching service, " +
      "store them as FailedPersonalDetailsValidation for unsuccessful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne
      val matchResult = MatchFailed

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.right[MatchingError](Future.successful(matchResult)))

      (matchingEventsSender.sendMatchResultEvent(_: MatchResult)(_: HeaderCarrier, _: ExecutionContext))
        .expects(matchResult, headerCarrier, executionContext)
        .returning(Future.successful(Done))

      val personalDetailsValidation = PersonalDetailsValidation.failed()

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(Future.successful(Done))

      validator.validate(personalDetails).value.futureValue shouldBe Right(personalDetailsValidation.id)
    }

    "return matching error when the call to match fails" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne

      val matchingError = MatchingError("error")
      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(EitherT.left[MatchResult](Future.successful(matchingError)))

      (matchingEventsSender.sendMatchingErrorEvent(_: HeaderCarrier, _: ExecutionContext))
        .expects(headerCarrier, executionContext)
        .returning(Future.successful(Done))

      validator.validate(personalDetails).value.futureValue shouldBe Left(matchingError)
    }
  }

  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val matchingConnector = mock[MatchingConnector]
    val matchingEventsSender = mock[MatchingEventsSender]

    val repository = mock[PersonalDetailsValidationRepository]
    implicit val uuidProvider: UUIDProvider = stub[UUIDProvider]
    uuidProvider.apply _ when() returns randomUUID()

    val validator = new PersonalDetailsValidator(matchingConnector, repository, matchingEventsSender)
  }
}
