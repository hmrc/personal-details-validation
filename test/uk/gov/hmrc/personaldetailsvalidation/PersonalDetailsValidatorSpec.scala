/*
 * Copyright 2017 HM Revenue & Customs
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
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.connectors.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.connectors.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
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

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(Future.successful(MatchSuccessful))

      val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(Future.successful(Done))

      validator.validate(personalDetails).futureValue shouldBe personalDetailsValidation.id
    }

    "match the given personal details with matching service, " +
      "store them as FailedPersonalDetailsValidation for unsuccessful match " +
      "and return the ValidationId" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(Future.successful(MatchFailed))

      val personalDetailsValidation = PersonalDetailsValidation.failed()

      (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(Future.successful(Done))

      validator.validate(personalDetails).futureValue shouldBe personalDetailsValidation.id
    }

    "return a failure when one is return from calling match" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne

      (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, headerCarrier, executionContext)
        .returning(Future.failed(new RuntimeException("error")))

      a[RuntimeException] should be thrownBy validator.validate(personalDetails).futureValue
    }

    Set(MatchSuccessful, MatchFailed) foreach { matchResult =>
      s"return a failure if match succeeds with $matchResult " +
        "and the repository returns a failure on PersonalDetailsValidation creation" in new Setup {
        val personalDetails = personalDetailsObjects.generateOne

        (matchingConnector.doMatch(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
          .expects(personalDetails, headerCarrier, executionContext)
          .returning(Future.successful(matchResult))

        (repository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
          .expects(*, executionContext)
          .returning(Future.failed(new RuntimeException("error")))

        a[RuntimeException] should be thrownBy validator.validate(personalDetails).futureValue
      }
    }
  }

  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val matchingConnector = mock[MatchingConnector]

    val repository = mock[PersonalDetailsValidationRepository]
    implicit val uuidProvider: UUIDProvider = stub[UUIDProvider]
    uuidProvider.apply _ when() returns randomUUID()

    val validator = new PersonalDetailsValidator(matchingConnector, repository)
  }
}
