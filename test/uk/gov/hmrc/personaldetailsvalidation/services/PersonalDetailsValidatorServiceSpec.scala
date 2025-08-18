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

package uk.gov.hmrc.personaldetailsvalidation.services

import generators.Generators.Implicits._
import generators.ObjectGenerators.successfulPersonalDetailsValidationObjects
import org.apache.pekko.Done
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpec
import support.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.mocks.repositories.MockPdvRepository
import uk.gov.hmrc.personaldetailsvalidation.model._

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global

class PersonalDetailsValidatorServiceSpec extends AnyWordSpec with UnitSpec with MockitoSugar with BeforeAndAfterEach {

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val randomValidationId: ValidationId                               = ValidationId(randomUUID)
  val personalDetailsValidation: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne

  val pdvService = new PersonalDetailsValidatorService(mockPdvRepository)

  override def beforeEach(): Unit = {
    reset(mockPdvRepository)
    super.beforeEach()
  }

  "PersonalDetailsValidatorService" should {
    "insert an instance of PDV into the PDV repository" in {
      MockPdvRepository.create(mockPdvRepository, personalDetailsValidation)

      val result = await(pdvService.insertRecord(personalDetailsValidation).value)

      result shouldBe Right(Done)
    }

    "return an instance of an pdv given a validation id" in {
      MockPdvRepository.get(mockPdvRepository, personalDetailsValidation.id)(personalDetailsValidation)

      val result = await(pdvService.getRecord(personalDetailsValidation.id))

      result shouldBe Some(personalDetailsValidation)
    }

    "return a None given an invalid validation id" in {
      MockPdvRepository.getError(mockPdvRepository, randomValidationId)

      val result = await(pdvService.getRecord(randomValidationId))

      result shouldBe None
    }
  }
}
