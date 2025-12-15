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

package uk.gov.hmrc.personaldetailsvalidation.formats

import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.personaldetailsvalidation.formats.AssociationFormat.associationFormat
import uk.gov.hmrc.personaldetailsvalidation.model.Association

import java.time.{Instant, LocalDateTime, ZoneOffset}

class AssociationFormatSpec extends UnitSpec with CommonTestData {

  val dateToMillis: LocalDateTime => Long = {localDateTime => localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli}

  val filterWhiteSpace: String => String = {s => s.replaceAll("\n", "").replaceAll(" ", "")}

  "AssociationFormat" should {

    "deserialize a valid Json representation of an association" in {

      val jsonAsString: String = createJson(testCredId, testSessionId, testValidationId, dateToMillis(testLastUpdated))

      val json: JsValue = Json.parse(jsonAsString)

      val expectedLastUpdated: LocalDateTime = Instant.ofEpochMilli(dateToMillis(testLastUpdated)).atZone(ZoneOffset.UTC).toLocalDateTime

      val expectedAssociation: Association = Association(testCredId, testSessionId, testValidationId, expectedLastUpdated)

      json.validate[Association] match {
        case JsSuccess(association, _) => association shouldBe expectedAssociation
        case JsError(error) => fail(s"Attempt to create instance of Association failed with error : $error")
      }
    }

    "serialize an instance of Association" in {

      val testAssociation: Association = Association(testCredId, testSessionId, testValidationId, testLastUpdated)

      val associationAsJson: String = Json.toJson(testAssociation).toString

      val expectedJson: String = createJson(testCredId, testSessionId, testValidationId, dateToMillis(testLastUpdated))

      associationAsJson shouldBe filterWhiteSpace(expectedJson)
    }
  }

  private def createJson(credentialId: String, sessionId: String, validationId: String, lastUpdatedInMillis: Long): String =
    s"""
       |{
       |  "credentialId" : "$credentialId",
       |  "sessionId" : "$sessionId",
       |  "validationId" : "$validationId",
       |  "lastUpdated" : { "$$date" :
       |                    { "$$numberLong" : "${lastUpdatedInMillis.toString}" }
       |                  }
       |}
       |""".stripMargin

}
