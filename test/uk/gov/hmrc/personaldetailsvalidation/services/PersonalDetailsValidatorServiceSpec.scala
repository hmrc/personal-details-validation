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

import org.apache.pekko.Done
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpec
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.mocks.repositories.MockPdvRepository
import uk.gov.hmrc.personaldetailsvalidation.model._

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global

class PersonalDetailsValidatorServiceSpec extends
  AnyWordSpec
  with UnitSpec
  with MockitoSugar
  with CommonTestData
  with BeforeAndAfterEach {

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val randomValidationId: ValidationId = ValidationId(randomUUID)

  val pdvService: PersonalDetailsValidatorService = new PersonalDetailsValidatorService(mockPdvRepository)

  override def beforeEach(): Unit = {
    reset(mockPdvRepository)
    super.beforeEach()
  }

  "PersonalDetailsValidatorService" should {
    "insert an instance of PDV into the PDV repository" in {
      MockPdvRepository.create(mockPdvRepository, personalDetailsValidationSuccess)

      val result = await(pdvService.insertRecord(personalDetailsValidationSuccess).value)

      result shouldBe Right(Done)
    }

    "return an instance of an pdv given a validation id" in {
      MockPdvRepository.get(mockPdvRepository, personalDetailsValidationSuccess.id)(personalDetailsValidationSuccess)

      val result: Option[PersonalDetailsValidation] = await(pdvService.getRecord(personalDetailsValidationSuccess.id))

      result shouldBe Some(personalDetailsValidationSuccess)
    }

    "return a None given an invalid validation id" in {
      MockPdvRepository.getError(mockPdvRepository, randomValidationId)

      val result: Option[PersonalDetailsValidation] = await(pdvService.getRecord(randomValidationId))

      result shouldBe None
    }
  }
}
