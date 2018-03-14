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

package uk.gov.hmrc.personaldetailsvalidation.formats

import java.util.UUID.randomUUID

import generators.Generators.Implicits._
import generators.ObjectGenerators._
import generators.{ObjectGenerators, PostCodeGenerator}
import org.scalacheck.Gen._
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.{JsResultException, Json}
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.uuid.UUIDProvider

class PersonalDetailsValidationFormatSpec
  extends UnitSpec
    with GeneratorDrivenPropertyChecks {

  import PersonalDetailsValidationFormat._
  import TinyTypesFormats._

  "format" should {
    "correctly parse valid postcodes" in new Setup {
      import PersonalDetailsFormat._
      forAll(PostCodeGenerator.postCode) { postCode =>
        val personalDetails : PersonalDetailsWithNino = ObjectGenerators.personalDetailsObjects.generateOne.asInstanceOf[PersonalDetailsWithNino]
        val personalDetailsWithPostCodeJson = new PersonalDetailsWithPostCode(
          personalDetails.firstName,
          personalDetails.lastName,
          personalDetails.dateOfBirth,
          postCode
        ).toJson.toString()
        Json.parse(personalDetailsWithPostCodeJson).as[PersonalDetails]
      }
    }

    "correctly fail to parse valid postcodes that do not start at the beginning of the field" in new Setup {
      import PersonalDetailsFormat._
      forAll(PostCodeGenerator.postCode) { postCode =>
        val personalDetails : PersonalDetailsWithNino = ObjectGenerators.personalDetailsObjects.generateOne.asInstanceOf[PersonalDetailsWithNino]
        val personalDetailsWithPostCodeJson = new PersonalDetailsWithPostCode(
          personalDetails.firstName,
          personalDetails.lastName,
          personalDetails.dateOfBirth,
          " " + postCode
        ).toJson.toString()
        an [JsResultException] should be thrownBy
          Json.parse(personalDetailsWithPostCodeJson).as[PersonalDetails]
      }
    }

    "correctly fail to parse valid postcodes that do not end the field" in new Setup {
      import PersonalDetailsFormat._
      forAll(PostCodeGenerator.postCode) { postCode =>
        val personalDetails : PersonalDetailsWithNino = ObjectGenerators.personalDetailsObjects.generateOne.asInstanceOf[PersonalDetailsWithNino]
        val personalDetailsWithPostCodeJson = new PersonalDetailsWithPostCode(
          personalDetails.firstName,
          personalDetails.lastName,
          personalDetails.dateOfBirth,
          postCode + " "
        ).toJson.toString()
        an [JsResultException] should be thrownBy
          Json.parse(personalDetailsWithPostCodeJson).as[PersonalDetails]
      }
    }

    "correctly fail to parse the field if it contains multiple postcode" in new Setup {
      import PersonalDetailsFormat._
      forAll(PostCodeGenerator.postCode) { postCode =>
        val secondPostCode = PostCodeGenerator.postCode.generateOne
        val personalDetails : PersonalDetailsWithNino = ObjectGenerators.personalDetailsObjects.generateOne.asInstanceOf[PersonalDetailsWithNino]
        val personalDetailsWithPostCodeJson = new PersonalDetailsWithPostCode(
          personalDetails.firstName,
          personalDetails.lastName,
          personalDetails.dateOfBirth,
          postCode + " " + secondPostCode
        ).toJson.toString()
        an [JsResultException] should be thrownBy
          Json.parse(personalDetailsWithPostCodeJson).as[PersonalDetails]
      }
    }

    "fail validation for invalid postcodes" in new Setup {
      import PersonalDetailsFormat._
      val invalidPostCode = listOfN[Char](7, frequency((1,choose(65.toChar, 90.toChar)), (1, choose(97.toChar, 122.toChar)))).map(_.mkString)
      forAll(invalidPostCode) { invalidPostCode: String =>
        val personalDetails : PersonalDetailsWithNino = ObjectGenerators.personalDetailsObjects.generateOne.asInstanceOf[PersonalDetailsWithNino]
        val personalDetailsWithPostCodeJson = new PersonalDetailsWithPostCode(
          personalDetails.firstName,
          personalDetails.lastName,
          personalDetails.dateOfBirth,
          invalidPostCode.mkString
        ).toJson.toString()
        an [JsResultException] should be thrownBy
          Json.parse(personalDetailsWithPostCodeJson).as[PersonalDetails]
      }
    }

    "allow to serialise SuccessfulPersonalDetailsValidation to JSON" in new Setup {
      forAll { personalDetails: PersonalDetails =>
        val personalDetailsWithNino = personalDetails.asInstanceOf[PersonalDetailsWithNino]
        toJson(PersonalDetailsValidation.successful(personalDetails)) shouldBe Json.obj(
          "id" -> ValidationId(uuidProvider()),
          "validationStatus" -> "success",
          "personalDetails" -> Json.obj(
            "firstName" -> personalDetailsWithNino.firstName,
            "lastName" -> personalDetailsWithNino.lastName,
            "dateOfBirth" -> personalDetailsWithNino.dateOfBirth,
            "nino" -> personalDetailsWithNino.nino
          )
        )
      }
    }

    "allow to serialise FailedPersonalDetailsValidation to JSON" in new Setup {
      toJson(PersonalDetailsValidation.failed()) shouldBe Json.obj(
        "id" -> ValidationId(uuidProvider()),
        "validationStatus" -> "failure"
      )
    }

    "allow to deserialise SuccessfulPersonalDetailsValidation to JSON" in new Setup {
      forAll { personalDetails: PersonalDetails =>
        val personalDetailsWithNino = personalDetails.asInstanceOf[PersonalDetailsWithNino]
        Json.obj(
          "id" -> ValidationId(uuidProvider()),
          "validationStatus" -> "success",
          "personalDetails" -> Json.obj(
            "firstName" -> personalDetailsWithNino.firstName,
            "lastName" -> personalDetailsWithNino.lastName,
            "dateOfBirth" -> personalDetailsWithNino.dateOfBirth,
            "nino" -> personalDetailsWithNino.nino
          )
        ).as[PersonalDetailsValidation] shouldBe PersonalDetailsValidation.successful(personalDetails)
      }
    }

    "allow to deserialise FailedPersonalDetailsValidation to JSON" in new Setup {
      Json.obj(
        "id" -> ValidationId(),
        "validationStatus" -> "failure"
      ).as[PersonalDetailsValidation] shouldBe PersonalDetailsValidation.failed()
    }
  }

  private trait Setup extends MockFactory {
    implicit val uuidProvider: UUIDProvider = new UUIDProvider {
      override lazy val apply = randomUUID()
    }
  }
}
