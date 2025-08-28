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

package uk.gov.hmrc.personaldetailsvalidation.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import support.CommonTestData
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, ValidationId, ValidationStatus}
import uk.gov.hmrc.uuid.UUIDProvider

import java.util.UUID

class PersonalDetailsValidationSpec extends AnyWordSpec with Matchers with CommonTestData {

  override val validationId: ValidationId = ValidationId(UUID.fromString(testValidationId))

  implicit val mockProvider: UUIDProvider = new UUIDProvider {
    override def apply(): UUID = validationId.value
  }

  "ValidationId" should {

    "wrap a UUID correctly" in {
      val uuid = UUID.fromString(testValidationId)
      val id = ValidationId(uuid)

      id.value shouldBe uuid
    }

    "generate a new UUID using UUIDProvider" in {
      val id = ValidationId()

      id.value.toString shouldBe testValidationId
    }

    "write to JSON correctly" in {
      val id   = ValidationId(UUID.fromString(testValidationId))
      val json = Json.toJson(id)(ValidationId.validationIdWrites)

      (json \ "value").as[UUID] shouldBe id.value
    }
  }

  "ValidationStatus" should {

    "contain Success and Failure values" in {
      ValidationStatus.all.map(_.value) should contain allOf("success", "failure")
    }

    "have lowercase string values" in {
      ValidationStatus.all.foreach { status =>
        status.value shouldBe status.typeName.toLowerCase
      }
    }
  }

  "SuccessfulPersonalDetailsValidation" should {

    "create a valid instance using the successful method" in {
      val validation = personalDetailsValidationSuccess

      validation.validationStatus shouldBe "success"
      validation.deceased shouldBe false
    }
  }

  "FailedPersonalDetailsValidation" should {

    "create a valid instance using the failed method" in {
      val validation = PersonalDetailsValidation.failed(testMaybeCredId, Some(2), dateTimeNow)

      validation.id.value.toString shouldBe testValidationId
      validation.validationStatus shouldBe "failure"
      validation.maybeCredId shouldBe testMaybeCredId
      validation.attempt shouldBe Some(2)
      validation.createdAt shouldBe dateTimeNow
    }
  }
}