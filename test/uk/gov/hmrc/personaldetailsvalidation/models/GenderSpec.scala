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
import play.api.libs.json.*
import uk.gov.hmrc.personaldetailsvalidation.model.Gender

class GenderSpec extends AnyWordSpec with Matchers {

  "Gender JSON reads" should {

    "successfully parse JSON with gender field present" in {
      val jsonStr =
        """
          |{
          |  "person": {
          |    "sex": "female"
          |  }
          |}
        """.stripMargin

      val json = Json.parse(jsonStr)
      val result = json.validate[Option[Gender]]

      result.isSuccess shouldBe true
      result.get shouldBe Some(Gender("female"))
    }

    "return None when gender field is missing" in {
      val jsonStr =
        """
          |{
          |  "person": {}
          |}
        """.stripMargin

      val json = Json.parse(jsonStr)
      val result = json.validate[Option[Gender]]

      result.isSuccess shouldBe true
      result.get shouldBe None
    }

    "return None when person field is missing" in {
      val jsonStr =
        """
          |{}
        """.stripMargin

      val json = Json.parse(jsonStr)
      val result = json.validate[Option[Gender]]

      result.isSuccess shouldBe true
      result.get shouldBe None
    }

    "fail when 'person.sex' is not a string" in {
      val jsonStr =
        """
          |{
          |  "person": {
          |    "sex": 123
          |  }
          |}
        """.stripMargin

      val json = Json.parse(jsonStr)
      val result = json.validate[Option[Gender]]

      result.isError shouldBe true
    }
  }
}