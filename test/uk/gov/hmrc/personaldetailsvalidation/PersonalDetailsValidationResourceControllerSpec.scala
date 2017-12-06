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

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import factory.ObjectFactory._
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalacheck.Gen
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.http.Status._
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsNull, Json}
import play.api.test.FakeRequest
import play.mvc.Http.HeaderNames.LOCATION
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetailsValidation.successfulPersonalDetailsValidation
import uk.gov.hmrc.personaldetailsvalidation.ValidationStatus.{Failure, Success}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.{ExecutionContext, Future}
import scalamock.MockArgumentMatchers

class PersonalDetailsValidationResourceControllerSpec extends UnitSpec with GeneratorDrivenPropertyChecks with ScalaFutures with MockFactory with MockArgumentMatchers {

  "Create in PersonalDetailsValidationResourceController" should {

    implicit val generator: Gen[PersonalDetails] = personalDetails

    "return CREATED http status code" in new Setup {
      forAll { personalDetails: PersonalDetails =>
        val personalDetailsValidation = successfulPersonalDetailsValidation(personalDetails)
        (mockRepository.create(_: PersonalDetailsValidation)(_: ExecutionContext)) expects(personalDetailsValidation, instanceOf[MdcLoggingExecutionContext]) returns Future.successful(Done)

        val response = controller.create()(request.withBody(toJson(personalDetails)))

        status(response) shouldBe CREATED
      }
    }

    "return uri of the new resource in response Location header" in new Setup {
      forAll { personalDetails: PersonalDetails =>
        val personalDetailsValidation = successfulPersonalDetailsValidation(personalDetails)
        (mockRepository.create(_: PersonalDetailsValidation)(_: ExecutionContext)) expects(personalDetailsValidation, instanceOf[MdcLoggingExecutionContext]) returns Future.successful(Done)

        val response = controller.create()(request.withBody(toJson(personalDetails))).futureValue

        response.header.headers(LOCATION) shouldBe routes.PersonalDetailsValidationResourceController.get(personalDetailsValidation.id).url
      }
    }

    "return bad request if mandatory fields are missing" in new Setup {
      val response = controller.create()(request.withBody(JsNull))

      status(response) shouldBe BAD_REQUEST
    }

    "return errors if mandatory fields are missing" in new Setup {
      val response = controller.create()(request.withBody(JsNull)).futureValue

      val errors = (jsonBodyOf(response) \ "errors").as[List[String]]
      errors should contain("firstName is missing")
      errors should contain("lastName is missing")
      errors should contain("dateOfBirth is missing")
      errors should contain("nino is missing")
    }
  }

  "Get in PersonalDetailsValidationResourceController" should {

    "return Not Found http status code if repository does not return personal details validation" in new Setup {
      (mockRepository.get(_: ValidationId)(_: ExecutionContext)) expects(personalDetailsValidationId, instanceOf[MdcLoggingExecutionContext]) returns Future.successful(None)

      val response = controller.get(personalDetailsValidationId)(request).futureValue

      status(response) shouldBe NOT_FOUND
    }

    "return OK http status code if repository returns personal details validation" in new Setup {
      (mockRepository.get(_: ValidationId)(_: ExecutionContext)) expects(personalDetailsValidationId, instanceOf[MdcLoggingExecutionContext]) returns Future.successful(Some(personalDetailsValidation))

      val response = controller.get(personalDetailsValidationId)(request).futureValue

      status(response) shouldBe OK
    }

    "return personalDetailsValidation id in response body" in new Setup {
      (mockRepository.get(_: ValidationId)(_: ExecutionContext)) expects(personalDetailsValidationId, instanceOf[MdcLoggingExecutionContext]) returns Future.successful(Some(personalDetailsValidation))

      val response = controller.get(personalDetailsValidationId)(request).futureValue

      (jsonBodyOf(response) \ "id").as[String] shouldBe personalDetailsValidation.id.value.toString
    }

    "return personal details in response body" in new Setup {
      (mockRepository.get(_: ValidationId)(_: ExecutionContext)) expects(personalDetailsValidationId, instanceOf[MdcLoggingExecutionContext]) returns Future.successful(Some(personalDetailsValidation))

      val response = controller.get(personalDetailsValidationId)(request).futureValue

      (jsonBodyOf(response) \ "personalDetails").toOption should contain(toJson(personalDetails))
    }

    "return validationStatus as success if repository returned successful personalDetailsValidation" in new Setup {
      override val personalDetailsValidation = randomPersonalDetailsValidation(personalDetails).copy(validationStatus = Success)
      (mockRepository.get(_: ValidationId)(_: ExecutionContext)) expects(personalDetailsValidationId, instanceOf[MdcLoggingExecutionContext]) returns Future.successful(Some(personalDetailsValidation))

      val response = controller.get(personalDetailsValidationId)(request).futureValue

      (jsonBodyOf(response) \ "validationStatus").as[String] shouldBe "success"
    }

    "return validationStatus as failure if repository returned failed personalDetailsValidation" in new Setup {
      override val personalDetailsValidation = randomPersonalDetailsValidation(personalDetails).copy(validationStatus = Failure)
      (mockRepository.get(_: ValidationId)(_: ExecutionContext)) expects(personalDetailsValidationId, instanceOf[MdcLoggingExecutionContext]) returns Future.successful(Some(personalDetailsValidation))

      val response = controller.get(personalDetailsValidationId)(request).futureValue

      (jsonBodyOf(response) \ "validationStatus").as[String] shouldBe "failure"
    }
  }

  trait Setup {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val personalDetailsWrites = Json.writes[PersonalDetails]
    implicit val uuidProvider: UUIDProvider = stub[UUIDProvider]
    uuidProvider.apply _ when() returns randomUUID()

    val personalDetails = randomPersonalDetails
    val personalDetailsValidation = randomPersonalDetailsValidation(personalDetails)

    val request = FakeRequest()
    val mockRepository = mock[PersonalDetailsValidationRepository]
    val controller = new PersonalDetailsValidationResourceController(mockRepository)(uuidProvider)
    val personalDetailsValidationId = ValidationId()
  }

}