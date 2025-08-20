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

import org.mockito.MockitoSugar.reset
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.mocks.services.{MockAssociationService, MockPdvService}
import uk.gov.hmrc.personaldetailsvalidation.model._

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AssociationControllerSpec extends UnitSpec
  with CommonTestData
  with BeforeAndAfterEach {

  val controller = new AssociationController(
      mockAssociationService,
      mockPersonalDetailsValidatorService,
      stubControllerComponents()
    )

  val association: Association = Association(
    testCredId,
    sessionId,
    validationId.value.toString,
    dateTimeNow
  )

  override def beforeEach(): Unit = {
    reset(mockPersonalDetailsValidatorService, mockAssociationService)
    super.beforeEach()
  }

  "retrieveRecord" should {
    s"return $OK" when {
      "record found" in {
        val successPDVRecord: SuccessfulPersonalDetailsValidation = SuccessfulPersonalDetailsValidation(
          id = validationId,
          personalDetails = PersonalDetailsWithNinoAndPostCode(
            "first", "last", LocalDate.of(2022, 12, 2), Nino("AA111111A"), "posty"),
          createdAt = dateTimeNow
        )
        MockAssociationService.getRecord(mockAssociationService)(Future.successful(Some(association)))
        MockPdvService.getRecord(mockPersonalDetailsValidatorService, validationId)(Some(successPDVRecord))

        val res: Result = await(controller.retrieveRecord()(FakeRequest("POST", "/").withBody(Json.obj("credentialId" -> "foo", "sessionId" -> "bar"))))
        status(res) shouldBe OK
      }
    }

    s"return $NOT_FOUND" when {
      "association record does not exist" in {
        MockAssociationService.getRecord(mockAssociationService)(Future.successful(None))

        val res: Result = await(controller.retrieveRecord()(FakeRequest("POST", "/").withBody(Json.obj("credentialId" -> "foo", "sessionId" -> "bar"))))

        status(res) shouldBe NOT_FOUND
      }

      "pdv record does not exist" in {
        MockAssociationService.getRecord(mockAssociationService)(Future.successful(Some(association)))
        MockPdvService.getRecord(mockPersonalDetailsValidatorService, validationId)(None)

        val res: Result = await(controller.retrieveRecord()(FakeRequest("POST", "/").withBody(Json.obj("credentialId" -> "foo", "sessionId" -> "bar"))))

        status(res) shouldBe NOT_FOUND
      }
    }
    s"return $BAD_REQUEST" when {
      "body invalid" in {
        val res: Result = await(controller.retrieveRecord()(FakeRequest("POST", "/").withBody(Json.obj("credentialIdFOO" -> "foo", "sessionId" -> "bar"))))

        status(res) shouldBe BAD_REQUEST
      }
    }
    s"return error" when {
      "service throws exception" in {
        MockAssociationService.getRecordFailure(mockAssociationService)

        intercept[Exception](await(controller.retrieveRecord()(FakeRequest("POST","/").withBody(Json.obj("credentialId" -> "foo", "sessionId" -> "bar")))))

      }
    }
  }
}
