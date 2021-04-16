/*
 * Copyright 2021 HM Revenue & Customs
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
import java.util.UUID
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{JsResultException, Json}
import play.api.libs.json.Json.toJson
import support.UnitSpec
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.uuid.UUIDProvider

class PersonalDetailsValidationFormatSpec
  extends UnitSpec
    with ScalaCheckDrivenPropertyChecks {

  import PersonalDetailsValidationFormat._
  import TinyTypesFormats._

  implicit val stringsOfAtLeast2Characters: Gen[String] = for {
    length <- Gen.chooseNum(2, 10)
    chars <- Gen.listOfN(length, Gen.alphaNumChar)
  } yield chars.mkString

  "format" should {
    "correctly parse valid postcodes" in new Setup {
      import PersonalDetailsFormat._
      forAll { personalDetailsWithPostCode : PersonalDetailsWithPostCode =>
        Json.parse(personalDetailsWithPostCode.toJson.toString()).as[PersonalDetails]
      }
    }

    "correctly fail to parse valid postcodes that do not start at the beginning of the field" in new Setup {
      import PersonalDetailsFormat._
      forAll { (personalDetailsWithPostCode : PersonalDetailsWithPostCode, randomString : String) =>
        val adjustedPostCode = randomString + personalDetailsWithPostCode.postCode.value
        an [JsResultException] should be thrownBy
          Json.parse(personalDetailsWithPostCode.copy(postCode = adjustedPostCode).toJson.toString()).as[PersonalDetails]
      }
    }

    "correctly fail to parse valid postcodes that do not end the field" in new Setup {
      import PersonalDetailsFormat._
      forAll { (personalDetailsWithPostCode : PersonalDetailsWithPostCode, randomString : String) =>
        val adjustedPostCode = personalDetailsWithPostCode.postCode.value + randomString
        an [JsResultException] should be thrownBy
          Json.parse(personalDetailsWithPostCode.copy(postCode = adjustedPostCode).toJson.toString()).as[PersonalDetails]
      }
    }

    "correctly fail to parse the field if it contains multiple postcodes" in new Setup {
      import PersonalDetailsFormat._
      forAll { (firstPersonalDetailsWithPostCode : PersonalDetailsWithPostCode, secondPersonalDetailsWithPostCode : PersonalDetailsWithPostCode, randomString : String) =>
        val adjustedPostCode =
          firstPersonalDetailsWithPostCode.postCode.value +
          randomString +
          secondPersonalDetailsWithPostCode.postCode.value
        an [JsResultException] should be thrownBy
          Json.parse(firstPersonalDetailsWithPostCode.copy(postCode = adjustedPostCode).toJson.toString()).as[PersonalDetails]
      }
    }

    "fail validation for invalid postcodes" in new Setup {
      import PersonalDetailsFormat._
      val badPostcodes = List("ZZ1 1ZZ","YI1 1YY")

      val personalDetailsWithPostCode=personalDetailsWithPostCodeObjects.generateOne

      badPostcodes.foreach { invalidPostCode: String =>
        an [JsResultException] should be thrownBy
          Json.parse(personalDetailsWithPostCode.copy(postCode = invalidPostCode).toJson.toString()).as[PersonalDetails]
      }
    }

    "allow to serialise SuccessfulPersonalDetailsValidation to JSON" in new Setup {
      forAll { personalDetailsWithNino: PersonalDetailsWithNino =>
        toJson(PersonalDetailsValidation.successful(personalDetailsWithNino)) shouldBe Json.obj(
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
      forAll { personalDetails: PersonalDetailsWithNino =>
        val personalDetailsWithNino = personalDetails
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
      override val apply: UUID = randomUUID()
    }
  }
}
