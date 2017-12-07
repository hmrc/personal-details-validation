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
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.uuid.UUIDProvider
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}

class PersonalDetailsValidatorSpec
  extends UnitSpec
    with ScalaFutures
    with MockFactory {

  "validate" should {

    "store the given PersonalDetails as SuccessfulPersonalDetailsValidation and return its ValidationId" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne
      val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)

      (mockRepository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(Future.successful(Done))

      validator.validate(personalDetails).futureValue shouldBe personalDetailsValidation.id
    }

    "return a failure if the repository returns one on PersonalDetailsValidation creation" in new Setup {
      val personalDetails = personalDetailsObjects.generateOne
      val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)

      (mockRepository.create(_: PersonalDetailsValidation)(_: ExecutionContext))
        .expects(personalDetailsValidation, executionContext)
        .returning(Future.failed(new RuntimeException("error")))

      a[RuntimeException] should be thrownBy validator.validate(personalDetails).futureValue
    }
  }

  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val mockRepository = mock[PersonalDetailsValidationRepository]
    implicit val uuidProvider: UUIDProvider = stub[UUIDProvider]
    uuidProvider.apply _ when() returns randomUUID()

    val validator = new PersonalDetailsValidator(mockRepository)
  }
}
