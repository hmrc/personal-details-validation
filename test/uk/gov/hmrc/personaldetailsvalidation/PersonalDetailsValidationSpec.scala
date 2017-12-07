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

import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.uuid.UUIDProvider

class PersonalDetailsValidationSpec
  extends UnitSpec
    with GeneratorDrivenPropertyChecks {

  "format" should {

    "allow to serialise successful PersonalDetailsValidation to JSON" in new Setup {
      forAll { personalDetails: PersonalDetails =>
        Json.toJson(PersonalDetailsValidation.successfulPersonalDetailsValidation(personalDetails)) shouldBe Json.obj(
          "id" -> ValidationId(uuidProvider()),
          "validationStatus" -> "success",
          "personalDetails" -> Json.obj(
            "firstName" -> personalDetails.firstName,
            "lastName" -> personalDetails.lastName,
            "dateOfBirth" -> personalDetails.dateOfBirth,
            "nino" -> personalDetails.nino
          )
        )
      }
    }

    "allow to serialise unsuccessful PersonalDetailsValidation to JSON" in new Setup {
      forAll { personalDetails: PersonalDetails =>
        Json.toJson(PersonalDetailsValidation.failedPersonalDetailsValidation(personalDetails)) shouldBe Json.obj(
          "id" -> ValidationId(uuidProvider()),
          "validationStatus" -> "failure",
          "personalDetails" -> Json.obj(
            "firstName" -> personalDetails.firstName,
            "lastName" -> personalDetails.lastName,
            "dateOfBirth" -> personalDetails.dateOfBirth,
            "nino" -> personalDetails.nino
          )
        )
      }
    }

    "allow to deserialise successful PersonalDetailsValidation to JSON" in new Setup {
      forAll { personalDetails: PersonalDetails =>
        Json.obj(
          "id" -> ValidationId(uuidProvider()),
          "validationStatus" -> "success",
          "personalDetails" -> Json.obj(
            "firstName" -> personalDetails.firstName,
            "lastName" -> personalDetails.lastName,
            "dateOfBirth" -> personalDetails.dateOfBirth,
            "nino" -> personalDetails.nino
          )
        ).as[PersonalDetailsValidation] shouldBe PersonalDetailsValidation.successfulPersonalDetailsValidation(personalDetails)
      }
    }

    "allow to deserialise unsuccessful PersonalDetailsValidation to JSON" in new Setup {
      forAll { personalDetails: PersonalDetails =>
        Json.obj(
          "id" -> ValidationId(uuidProvider()),
          "validationStatus" -> "failure",
          "personalDetails" -> Json.obj(
            "firstName" -> personalDetails.firstName,
            "lastName" -> personalDetails.lastName,
            "dateOfBirth" -> personalDetails.dateOfBirth,
            "nino" -> personalDetails.nino
          )
        ).as[PersonalDetailsValidation] shouldBe PersonalDetailsValidation.failedPersonalDetailsValidation(personalDetails)
      }
    }
  }

  private trait Setup extends MockFactory {
    implicit val uuidProvider: UUIDProvider = new UUIDProvider {
      override lazy val apply = Gen.uuid.generateOne
    }
  }
}
