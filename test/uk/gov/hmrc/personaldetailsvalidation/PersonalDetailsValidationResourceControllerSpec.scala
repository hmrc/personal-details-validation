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

import java.util.UUID.randomUUID

import akka.stream.Materializer
import factory.ObjectFactory._
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalacheck.Arbitrary
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsNull, JsUndefined, Json, Writes}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.{ExecutionContext, Future}
import scalamock.MockArgumentMatchers

class PersonalDetailsValidationResourceControllerSpec
  extends UnitSpec
    with PropertyChecks
    with ScalaFutures
    with MockFactory
    with MockArgumentMatchers {

  "create" should {

    implicit val generator: Arbitrary[PersonalDetails] = asArbitrary(personalDetailsObjects)

    "tell the PersonalDetailsValidator to validate the given personal details " +
      "and return CREATED when it completes" in new Setup {
      forAll { (personalDetails: PersonalDetails, validationId: ValidationId) =>
        (mockValidator.validate(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
          .expects(personalDetails, instanceOf[HeaderCarrier], instanceOf[MdcLoggingExecutionContext])
          .returns(Future.successful(validationId))

        val response = controller.create(request.withBody(toJson(personalDetails)))

        status(response) shouldBe CREATED
      }
    }

    "return uri of the personal-details-validation/:id in the response Location header" in new Setup {
      forAll { (personalDetails: PersonalDetails, validationId: ValidationId) =>
        (mockValidator.validate(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
          .expects(personalDetails, instanceOf[HeaderCarrier], instanceOf[MdcLoggingExecutionContext])
          .returns(Future.successful(validationId))

        val response = controller.create(request.withBody(toJson(personalDetails)))

        header(LOCATION, response) shouldBe Some(routes.PersonalDetailsValidationResourceController.get(validationId).url)
      }
    }

    "return failed future if PersonalDetailsValidator returns one" in new Setup {
      val personalDetails = randomPersonalDetails

      (mockValidator.validate(_: PersonalDetails)(_: HeaderCarrier, _: ExecutionContext))
        .expects(personalDetails, instanceOf[HeaderCarrier], instanceOf[MdcLoggingExecutionContext])
        .returns(Future.failed(new RuntimeException("error")))

      a[RuntimeException] should be thrownBy controller.create(request.withBody(toJson(personalDetails))).futureValue
    }

    "return BAD_REQUEST if mandatory fields are missing" in new Setup {
      val response = controller.create(request.withBody(JsNull))

      status(response) shouldBe BAD_REQUEST
    }

    "return errors if mandatory fields are missing" in new Setup {
      val response = controller.create(request.withBody(JsNull)).futureValue

      (jsonBodyOf(response) \ "errors").as[List[String]] should contain only(
        "firstName is missing",
        "lastName is missing",
        "dateOfBirth is missing",
        "nino is missing"
      )
    }
  }

  "Get in PersonalDetailsValidationResourceController" should {

    implicit val generator: Arbitrary[PersonalDetailsValidation] = asArbitrary(personalDetailsValidationObjects)

    "return Not Found http status code if repository does not return personal details validation" in new Setup {
      val validationId = ValidationId()

      (mockRepository.get(_: ValidationId)(_: ExecutionContext))
        .expects(validationId, instanceOf[MdcLoggingExecutionContext])
        .returns(Future.successful(None))

      val response = controller.get(validationId)(request).futureValue

      status(response) shouldBe NOT_FOUND
    }

    "return OK http status code if repository returns personal details validation" in new Setup {
      forAll { personalDetailsValidation: PersonalDetailsValidation =>
        (mockRepository.get(_: ValidationId)(_: ExecutionContext))
          .expects(personalDetailsValidation.id, instanceOf[MdcLoggingExecutionContext])
          .returns(Future.successful(Some(personalDetailsValidation)))

        val response = controller.get(personalDetailsValidation.id)(request).futureValue

        status(response) shouldBe OK
      }
    }

    "return personalDetailsValidation id in response body" in new Setup {
      forAll { personalDetailsValidation: PersonalDetailsValidation =>
        (mockRepository.get(_: ValidationId)(_: ExecutionContext))
          .expects(personalDetailsValidation.id, instanceOf[MdcLoggingExecutionContext])
          .returns(Future.successful(Some(personalDetailsValidation)))

        val response = controller.get(personalDetailsValidation.id)(request).futureValue

        (jsonBodyOf(response) \ "id").as[String] shouldBe personalDetailsValidation.id.value.toString
      }
    }

    "return personal details in response body for SuccessfulPersonalDetailsValidation" in new Setup {

      import formats.PersonalDetailsFormat._

      forAll { personalDetailsValidation: SuccessfulPersonalDetailsValidation =>
        (mockRepository.get(_: ValidationId)(_: ExecutionContext))
          .expects(personalDetailsValidation.id, instanceOf[MdcLoggingExecutionContext])
          .returns(Future.successful(Some(personalDetailsValidation)))

        val response = controller.get(personalDetailsValidation.id)(request).futureValue

        (jsonBodyOf(response) \ "personalDetails").as[PersonalDetails] shouldBe personalDetailsValidation.personalDetails
      }
    }

    "do not return personal details in response body for FailedPersonalDetailsValidation" in new Setup {

      forAll { personalDetailsValidation: FailedPersonalDetailsValidation =>
        (mockRepository.get(_: ValidationId)(_: ExecutionContext))
          .expects(personalDetailsValidation.id, instanceOf[MdcLoggingExecutionContext])
          .returns(Future.successful(Some(personalDetailsValidation)))

        val response = controller.get(personalDetailsValidation.id)(request).futureValue

        (jsonBodyOf(response) \ "personalDetails") shouldBe a[JsUndefined]
      }
    }

    val validationStatusScenarios = Table(
      ("personalDetailsValidation", "result"),
      (successfulPersonalDetailsValidationObjects.generateOne, "success"),
      (failedPersonalDetailsValidationObjects.generateOne, "failure")
    )

    forAll(validationStatusScenarios) { (personalDetailsValidation, status) =>
      s"return validationStatus as $status if repository returned ${personalDetailsValidation.getClass.getSimpleName}" in new Setup {
        (mockRepository.get(_: ValidationId)(_: ExecutionContext))
          .expects(personalDetailsValidation.id, instanceOf[MdcLoggingExecutionContext])
          .returns(Future.successful(Some(personalDetailsValidation)))

        val response = controller.get(personalDetailsValidation.id)(request).futureValue

        (jsonBodyOf(response) \ "validationStatus").as[String] shouldBe status
      }
    }
  }

  private trait Setup {
    implicit val materializer: Materializer = mock[Materializer]
    implicit val personalDetailsWrites: Writes[PersonalDetails] = Json.writes[PersonalDetails]
    implicit val uuidProvider: UUIDProvider = stub[UUIDProvider]
    uuidProvider.apply _ when() returns randomUUID()

    val request = FakeRequest()
    val mockRepository = mock[PersonalDetailsValidationRepository]
    val mockValidator = mock[PersonalDetailsValidator]
    val controller = new PersonalDetailsValidationResourceController(mockRepository, mockValidator)
  }
}