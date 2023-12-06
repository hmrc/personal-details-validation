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

package uk.gov.hmrc.personaldetailsvalidation.services

import akka.Done
import cats.data.EitherT
import generators.ObjectGenerators.successfulPersonalDetailsValidationObjects
import org.scalamock.scalatest.MockFactory
import support.UnitSpec
import uk.gov.hmrc.personaldetailsvalidation.PdvRepository
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, SuccessfulPersonalDetailsValidation, ValidationId}
import generators.Generators.Implicits._

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}



class PersonalDetailsValidatorServiceSpec extends UnitSpec with MockFactory {

  "PersonalDetailsValidatorService" should {
    "insert an instance of PDV into the PDV repository" in new Setup {
      (mockRepository.create(_:PersonalDetailsValidation)(_ : ExecutionContext)).expects(personalDetailsValidation, ExecutionContext.global).returning(EitherT.rightT[Future, Exception](Done))
      await(pdvService.insertRecord(personalDetailsValidation).value) shouldBe Right(Done)
    }

    "return an instance of an pdv given a validation id" in new Setup {
      (mockRepository.get(_: ValidationId)(_ : ExecutionContext)).expects(personalDetailsValidation.id, ExecutionContext.global).returning(Future.successful(Some(personalDetailsValidation)))
      await(pdvService.getRecord(personalDetailsValidation.id)) shouldBe Some(personalDetailsValidation)
    }

    "return a None given an invalid validation id" in new Setup {
      (mockRepository.get(_: ValidationId)(_ : ExecutionContext)).expects(randomValidationId, ExecutionContext.global).returning(Future.successful(None))
      await(pdvService.getRecord(randomValidationId)) shouldBe None
    }
  }
  trait Setup {
    val randomValidationId: ValidationId = ValidationId(randomUUID)
    val personalDetailsValidation: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
    val mockRepository: PdvRepository = mock[PdvRepository]
    val pdvService = new PersonalDetailsValidatorService(mockRepository)
  }
}
