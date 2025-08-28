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

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import support.CommonTestData
import uk.gov.hmrc.personaldetailsvalidation.model.{Association, RetrieveAssociation}
import play.api.libs.json._
import java.time.LocalDateTime

class AssociationSpec extends AnyWordSpec with Matchers with CommonTestData {

  "RetrieveAssociation" should {

    "read valid JSON correctly" in {
      val jsonStr =
        s"""
           |{
           |  "credentialId": "$testCredId",
           |  "sessionId": "$sessionId"
           |}
        """.stripMargin

      val json   = Json.parse(jsonStr)
      val result = json.validate[RetrieveAssociation]

      result.isSuccess shouldBe true
      result.get shouldBe RetrieveAssociation(s"$testCredId", s"$sessionId")
    }

    "fail to read JSON with missing fields" in {
      val jsonStr =
        s"""
           |{
           |  "credentialId": "$testCredId"
           |}
        """.stripMargin

      val json   = Json.parse(jsonStr)
      val result = json.validate[RetrieveAssociation]

      result.isError shouldBe true
    }

    "fail to read JSON with incorrect types" in {
      val jsonStr =
        s"""
           |{
           |  "credentialId": 123,
           |  "sessionId": true
           |}
        """.stripMargin

      val json = Json.parse(jsonStr)
      val result = json.validate[RetrieveAssociation]

      result.isError shouldBe true
    }
  }

  "Association" should {

    "create an instance with correct values" in {
      val now   = LocalDateTime.now()
      val assoc = Association(s"$testCredId", s"$sessionId", s"$testValidationId", now)

      assoc.credentialId shouldBe s"$testCredId"
      assoc.sessionId    shouldBe s"$sessionId"
      assoc.validationId shouldBe s"$testValidationId"
      assoc.lastUpdated  shouldBe now
    }

    "write and read to/from JSON correctly" in {
      implicit val associationFormat: Format[Association] = Json.format[Association]

      val now         = LocalDateTime.of(2025, 8, 26, 15, 0)
      val association = Association(s"$testCredId", s"$sessionId", s"$testValidationId", now)

      val json    = Json.toJson(association)
      val parsed  = json.validate[Association]

      parsed.isSuccess shouldBe true
      parsed.get       shouldBe association
    }
  }
}