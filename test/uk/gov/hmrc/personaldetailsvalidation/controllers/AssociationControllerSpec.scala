/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalamock.scalatest.MockFactory
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import scalamock.MockArgumentMatchers
import support.UnitSpec
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.model.{Association, PersonalDetailsWithNinoAndPostCode, SuccessfulPersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.personaldetailsvalidation.services.{AssociationService, PersonalDetailsValidatorService}

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AssociationControllerSpec extends UnitSpec
  with GuiceOneAppPerSuite
  with MockFactory
  with MockArgumentMatchers {

  class Setup {
    val mockAssociationService = mock[AssociationService]
    val mockPersonalDetailsValidatorService = mock[PersonalDetailsValidatorService]
    val controller = new AssociationController(mockAssociationService, mockPersonalDetailsValidatorService, stubControllerComponents())
  }
  "retrieveRecord" should {
    s"return $OK" when {
      "record found" in new Setup {
        val credId = "cred"
        val sessionId = "sesh"
        val dateTimeNow = LocalDateTime.of(2020,1,1,1,1)
        val validationId = ValidationId(UUID.fromString("928b39f3-98f7-4a0b-bcfe-9065c1175d1e"))
        val association = Association(
          credId,
          sessionId,
          validationId.value.toString,
          dateTimeNow
        )
        val successPDVRecord = SuccessfulPersonalDetailsValidation(
          id = validationId,
          personalDetails = PersonalDetailsWithNinoAndPostCode(
            "first","last", LocalDate.of(2022,12,2), Nino("AA111111A"), "posty"),
          createdAt = dateTimeNow
        )
        (mockAssociationService.getRecord(_: String, _: String)).expects("foo", "bar").returning(Future.successful(Some(association)))
        (mockPersonalDetailsValidatorService.getRecord(_: ValidationId)(_: ExecutionContext)).expects(validationId, *).returning(Future.successful(Some(successPDVRecord)))
        val res = await(controller.retrieveRecord()(FakeRequest("POST","/").withBody(Json.obj("credentialId" -> "foo", "sessionId" -> "bar"))))
        status(res) shouldBe OK
      }
    }
    s"return $NOT_FOUND" when {
      "association record does not exist" in new Setup {
        (mockAssociationService.getRecord(_: String, _: String)).expects("foo", "bar").returning(Future.successful(None))
        val res = await(controller.retrieveRecord()(FakeRequest("POST","/").withBody(Json.obj("credentialId" -> "foo", "sessionId" -> "bar"))))
        status(res) shouldBe NOT_FOUND
      }
      "pdv record does not exist" in new Setup {
        val credId = "cred"
        val sessionId = "sesh"
        val dateTimeNow = LocalDateTime.of(2020,1,1,1,1)
        val validationId = ValidationId(UUID.fromString("928b39f3-98f7-4a0b-bcfe-9065c1175d1e"))
        val association = Association(
          credId,
          sessionId,
          validationId.value.toString,
          dateTimeNow
        )

        (mockAssociationService.getRecord(_: String, _: String)).expects("foo", "bar").returning(Future.successful(Some(association)))
        (mockPersonalDetailsValidatorService.getRecord(_: ValidationId)(_: ExecutionContext)).expects(validationId, *).returning(Future.successful(None))
        val res = await(controller.retrieveRecord()(FakeRequest("POST","/").withBody(Json.obj("credentialId" -> "foo", "sessionId" -> "bar"))))
        status(res) shouldBe NOT_FOUND
      }
    }
    s"return $BAD_REQUEST" when {
      "body invalid" in new Setup {
        val res = await(controller.retrieveRecord()(FakeRequest("POST","/").withBody(Json.obj("credentialIdFOO" -> "foo", "sessionId" -> "bar"))))
        status(res) shouldBe BAD_REQUEST
      }
    }
    s"return error" when {
      "service throws exception" in new Setup {
        (mockAssociationService.getRecord(_: String, _: String)).expects("foo", "bar").returning(Future.failed(new Exception("uh oh")))
        intercept[Exception](await(controller.retrieveRecord()(FakeRequest("POST","/").withBody(Json.obj("credentialId" -> "foo", "sessionId" -> "bar")))))


      }
    }
  }
}
