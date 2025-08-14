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

package uk.gov.hmrc.personaldetailsvalidation.controllers

import play.api.http.MimeTypes
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import play.api.libs.json.{JsNull, Json}
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.mvc.Http.HeaderNames.CONTENT_TYPE
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.personaldetailsvalidation.services.{AssociationService, Encryption, PersonalDetailsValidatorService}
import uk.gov.hmrc.support.utils.BaseIntegrationSpec

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AssociationControllerISpec extends BaseIntegrationSpec with FutureAwaits with DefaultAwaitTimeout {

  val associationService: AssociationService = app.injector.instanceOf[AssociationService]
  val personalDetailsValidationService: PersonalDetailsValidatorService = app.injector.instanceOf[PersonalDetailsValidatorService]
  val encryption: Encryption = app.injector.instanceOf[Encryption]

  def retrieveBySession(body: String): Future[WSResponse] =
    wsUrl("/personal-details-validation/retrieve-by-session")
      .addHttpHeaders(CONTENT_TYPE -> MimeTypes.JSON)
      .post(body)

  "/retrieve-by-session" should {
    s"return $OK" when {
      "association record exists & PDV record exists and PDV record is success" in {
        val credId = "cred1Foo"
        val sessionId = "sessionId1Foo"
        val dateTimeNow = LocalDateTime.of(2020,1,1,1,1)
        val validationId = ValidationId(UUID.fromString("928b39f3-98f7-4a0b-bcfe-9065c1175d1e"))
        val association = Association(
          encryption.crypto.encrypt(PlainText(credId)).value,
          encryption.crypto.encrypt(PlainText(sessionId)).value,
          validationId.value.toString,
          dateTimeNow
        )

        val successPDVRecord = SuccessfulPersonalDetailsValidation(
          id = validationId,
          personalDetails = PersonalDetailsWithNinoAndPostCode(
            "first","last", LocalDate.of(2022,12,2), Nino("AA111111A"), "posty"),
          createdAt = dateTimeNow
        )
        await(associationService.insertRecord(association))
        await(personalDetailsValidationService.insertRecord(successPDVRecord).value)

        val result = await(retrieveBySession(Json.obj("credentialId" -> credId, "sessionId" -> sessionId).toString()))

        result.status mustBe OK
        result.json mustBe Json.obj(
          "id" -> "928b39f3-98f7-4a0b-bcfe-9065c1175d1e",
          "validationStatus" -> "success",
          "personalDetails" -> Json.obj(
            "firstName" -> "first",
            "lastName" -> "last",
            "dateOfBirth" -> "2022-12-02",
            "postCode" -> "posty",
            "nino" -> "AA111111A"
          ),
          "createdAt" -> Json.obj(
            "$date" -> Json.obj(
              "$numberLong" -> "1577840460000"
            )
          ),
          "deceased" -> false
        )

      }
      "association record exists & PDV record exists and PDV record is fail" in {
        val credId = "cred1Foo"
        val sessionId = "sessionId1Foo"
        val dateTimeNow = LocalDateTime.of(2020,1,1,1,1)
        val validationId = ValidationId(UUID.fromString("928b39f3-98f7-4a0b-bcfe-9065c1175d1e"))
        val association = Association(
          encryption.crypto.encrypt(PlainText(credId)).value,
          encryption.crypto.encrypt(PlainText(sessionId)).value,
          validationId.value.toString,
          dateTimeNow
        )

        val failPDVRecord = FailedPersonalDetailsValidation(
          id = validationId,
          createdAt = dateTimeNow)
        await(associationService.insertRecord(association))
        await(personalDetailsValidationService.insertRecord(failPDVRecord).value)

        val result = await(retrieveBySession(Json.obj("credentialId" -> credId, "sessionId" -> sessionId).toString()))

        result.status mustBe OK
        result.json mustBe Json.obj(
          "id" -> "928b39f3-98f7-4a0b-bcfe-9065c1175d1e",
          "validationStatus" -> "failure",
          "attempts" -> JsNull,
          "credentialId" -> JsNull,
          "createdAt" -> Json.obj(
            "$date" -> Json.obj(
              "$numberLong" -> "1577840460000"
            )
          )
        )
      }
    }
    s"return $NOT_FOUND" when {
      "association record does not exist" in {
        val credId = "cred1Foo"
        val sessionId = "sessionId1Foo"
        val result = await(retrieveBySession(Json.obj("credentialId" -> credId, "sessionId" -> sessionId).toString()))

        result.status mustBe NOT_FOUND
        result.json mustBe Json.obj("error" -> "No association found")
      }
      "pdv record does not exist" in {
        val credId = "cred1Foo"
        val sessionId = "sessionId1Foo"
        val dateTimeNow = LocalDateTime.of(2020,1,1,1,1)
        val validationId = ValidationId(UUID.fromString("928b39f3-98f7-4a0b-bcfe-9065c1175d1e"))
        val association = Association(
          encryption.crypto.encrypt(PlainText(credId)).value,
          encryption.crypto.encrypt(PlainText(sessionId)).value,
          validationId.value.toString,
          dateTimeNow
        )
        await(associationService.insertRecord(association))
        val result = await(retrieveBySession(Json.obj("credentialId" -> credId, "sessionId" -> sessionId).toString()))
        result.status mustBe NOT_FOUND
        result.json mustBe Json.obj("error" -> s"No record found using validation ID ${validationId.value}")
      }
    }
    s"return $BAD_REQUEST" when {
      "missing parameters" in {
        val result = await(retrieveBySession(Json.obj("credentialId" -> "credId").toString()))
        result.status mustBe BAD_REQUEST
      }
      "sessionId is blank" in {
        val result = await(retrieveBySession(Json.obj("credentialId" -> "foo", "sessionId" -> "").toString()))
        result.status mustBe BAD_REQUEST
      }
      "credId is blank" in {
        val result = await(retrieveBySession(Json.obj("credentialId" -> "", "sessionId" -> "foo").toString()))
        result.status mustBe BAD_REQUEST
      }
    }
  }
}
