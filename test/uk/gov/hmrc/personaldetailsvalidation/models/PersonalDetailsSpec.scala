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
import support.CommonTestData
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.model.NonEmptyString
import uk.gov.hmrc.personaldetailsvalidation.model.*

import java.time.{LocalDate, Period}

class PersonalDetailsSpec extends AnyWordSpec with Matchers with CommonTestData {


  val firstName: NonEmptyString = NonEmptyString("Jane")
  val lastName: NonEmptyString = NonEmptyString("Doe")
  val dateOfBirth: LocalDate = LocalDate.of(1990, 1, 1)
  val postCode: NonEmptyString = NonEmptyString("AB12CD")
  val gender: NonEmptyString = NonEmptyString("female")
  val testNino: Nino = Nino("AA123456A")

  "PersonalDetailsWithPostCode" should {

    "write to JSON correctly" in {
      val details = PersonalDetailsWithPostCode(firstName, lastName, dateOfBirth, postCode)
      val json = details.toJson

      (json \ "firstName").as[NonEmptyString] shouldBe firstName
      (json \ "lastName").as[NonEmptyString] shouldBe lastName
      (json \ "dateOfBirth").as[LocalDate] shouldBe dateOfBirth
      (json \ "postCode").as[String] shouldBe postCode.value
    }

    "add NINO and return PersonalDetailsWithNinoAndPostCode" in {
      val details = PersonalDetailsWithPostCode(firstName, lastName, dateOfBirth, postCode)
      val withNino = details.addNino(testNino)

      withNino shouldBe a[PersonalDetailsWithNinoAndPostCode]
      withNino.asInstanceOf[PersonalDetailsWithNinoAndPostCode].nino shouldBe testNino
    }

    "calculate age correctly" in {
      val details = PersonalDetailsWithPostCode(firstName, lastName, dateOfBirth, postCode)
      details.age shouldBe Period.between(dateOfBirth, LocalDate.now()).getYears
    }
  }

  "PersonalDetailsWithNino" should {

    "write to JSON correctly" in {
      val details = PersonalDetailsWithNino(firstName, lastName, dateOfBirth, testNino)
      val json = details.toJson

      (json \ "nino").as[Nino] shouldBe testNino
    }

    "add gender and return PersonalDetailsWithNinoAndGender" in {
      val details = PersonalDetailsWithNino(firstName, lastName, dateOfBirth, testNino)
      val withGender = details.addGender(gender)

      withGender shouldBe a[PersonalDetailsWithNinoAndGender]
      withGender.asInstanceOf[PersonalDetailsWithNinoAndGender].gender shouldBe gender
    }

    "return maybeNino correctly" in {
      val details = PersonalDetailsWithNino(firstName, lastName, dateOfBirth, testNino)
      details.maybeNino shouldBe Some(testNino)
    }
  }

  "PersonalDetailsWithNinoAndGender" should {

    "write to JSON correctly" in {
      val details = PersonalDetailsWithNinoAndGender(firstName, lastName, dateOfBirth, testNino, gender)
      val json = details.toJson

      (json \ "gender").as[NonEmptyString] shouldBe gender
    }

    "return maybeGender correctly" in {
      val details = PersonalDetailsWithNinoAndGender(firstName, lastName, dateOfBirth, testNino, gender)
      details.maybeGender shouldBe Some(gender)
    }
  }

  "PersonalDetailsWithNinoAndPostCode" should {

    "add gender and return PersonalDetailsWithNinoAndPostCodeAndGender" in {
      val details = PersonalDetailsWithNinoAndPostCode(firstName, lastName, dateOfBirth, testNino, postCode)
      val withGender = details.addGender(gender)

      withGender shouldBe a[PersonalDetailsWithNinoAndPostCodeAndGender]
      withGender.asInstanceOf[PersonalDetailsWithNinoAndPostCodeAndGender].gender shouldBe gender
    }
  }

  "PersonalDetails.replacePostCode" should {

    "replace postCode with nino and return PersonalDetailsWithNino" in {
      val detailsWithPostCode = PersonalDetailsWithPostCode(firstName, lastName, dateOfBirth, postCode)
      val testNino = Nino("AA123456A")

      val detailsWithNino = new PersonalDetailsNino {
        def nino: Nino = testNino
      }

      val replaced = PersonalDetails.replacePostCode(detailsWithPostCode, detailsWithNino)

      replaced shouldBe a[PersonalDetailsWithNino]
      replaced.nino shouldBe testNino
    }
  }
}
