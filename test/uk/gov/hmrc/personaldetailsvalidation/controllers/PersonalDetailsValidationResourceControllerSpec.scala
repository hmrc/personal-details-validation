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

import factory.ObjectFactory._
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.apache.pekko.stream.Materializer
import org.scalacheck.Arbitrary
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import support.UnitSpec
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.personaldetailsvalidation._
import uk.gov.hmrc.personaldetailsvalidation.mocks.MockValidator
import uk.gov.hmrc.personaldetailsvalidation.mocks.connectors.MockAuthConnector
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.personaldetailsvalidation.services.PersonalDetailsValidatorService
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PersonalDetailsValidationResourceControllerSpec
  extends AnyWordSpec
    with UnitSpec
    with ScalaCheckPropertyChecks
    with ScalaFutures
    with Matchers
    with GuiceOneAppPerSuite
    with TableDrivenPropertyChecks {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(Map("metrics.enabled" -> "false"))
      .build()

  "create" should {

    implicit val personalDetailsGenerator: Arbitrary[PersonalDetails] = asArbitrary(personalDetailsObjects)
    implicit val personalDetailsValidationGenerator: Arbitrary[PersonalDetailsValidation] = asArbitrary(personalDetailsValidationObjects)

    "return CREATED when personal details are validated with no error" in new Setup {
      forAll { (personalDetails: PersonalDetails, personalDetailsValidation: PersonalDetailsValidation) =>
        val requestWithBody = request.withBody(toJson(personalDetails))

        MockAuthConnector.authoriseSuccess(mockAuthConnector)
        MockValidator.validate(mockValidator, personalDetails, origin, maybeCredId)(personalDetailsValidation)

        val response = controller.create(origin)(requestWithBody)

        status(response) shouldBe CREATED
      }
    }

    val ninoTransformationScenarios: TableFor3[String, String, String] = Table(
      ("scenario", "originalNinoValue", "finalNinoValue"),
      ("convert nino value to UPPER CASE", "aa000003d", "AA000003D"),
      ("remove intermediate spaces in nino value", "aa 00 00 03 d", "AA000003D")
    )

    forAll(ninoTransformationScenarios) { (scenario, originalNinoValue, finalNinoValue) =>
      s"$scenario" in new Setup {
        val personalDetailsValidation: PersonalDetailsValidation = personalDetailsValidationObjects.generateOne
        val requestPersonalDetails: PersonalDetailsWithNino = randomPersonalDetails.asInstanceOf[PersonalDetailsWithNino]
        val json: JsObject =
          Json.toJson[PersonalDetails](requestPersonalDetails).as[JsObject] + ("nino" -> JsString(originalNinoValue))
        val requestWithBody: FakeRequest[JsObject] = request.withBody(json)

        val personalDetailsWithUpperCaseNino: PersonalDetailsWithNino = requestPersonalDetails.copy(nino = Nino(finalNinoValue))

        MockAuthConnector.authoriseSuccess(mockAuthConnector)
        MockValidator.validate(mockValidator, personalDetailsWithUpperCaseNino, origin, maybeCredId)(personalDetailsValidation)

        val response: Future[Result] = controller.create(origin)(requestWithBody)

        status(response) shouldBe CREATED
      }
    }

    "return uri of the personal-details-validation/:id in the response Location header" in new Setup {
      forAll { (personalDetails: PersonalDetails) =>
        val requestWithBody           = request.withBody(toJson(personalDetails))
        val personalDetailsValidation = personalDetailsValidationObjects.generateOne

        MockAuthConnector.authoriseSuccess(mockAuthConnector)
        MockValidator.validate(mockValidator, personalDetails, origin, maybeCredId)(personalDetailsValidation)

        val response = controller.create(origin)(requestWithBody)

        header(LOCATION, response) shouldBe Some(controllers.routes.PersonalDetailsValidationResourceController.get(personalDetailsValidation.id).url)
      }
    }

    "return validation response of the personal-details-validation/:id in the response body" in new Setup {
      forAll { (personalDetails: PersonalDetails, personalDetailsValidation: PersonalDetailsValidation) =>
        val requestWithBody = request.withBody(toJson(personalDetails))

        MockAuthConnector.authoriseSuccess(mockAuthConnector)
        MockValidator.validateNoId(mockValidator, personalDetails, origin)(personalDetailsValidation)

//        (mockValidator.validate(_: PersonalDetails, _ : Option[String], _ : Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
//          .expects(personalDetails, origin, *, instanceOf[HeaderCarrier], requestWithBody, *)
//          .returns(EitherT.rightT[Future, Exception](personalDetailsValidation))

        val response = controller.create(origin)(requestWithBody)

        jsonBodyOf(response).futureValue shouldBe toJson(personalDetailsValidation)
      }
    }
  }
//
//    "return BadGatewayException if matching error occurs" in new Setup {
//      val personalDetails: PersonalDetails = randomPersonalDetails
//
//      val badGatewayException = new BadGatewayException("some error")
//      val requestWithBody: FakeRequest[JsValue] = request.withBody(toJson(personalDetails))
//
//      (mockAuthConnector.authorise[Option[Credentials]](_: Predicate, _: Retrieval[Option[Credentials]])(_: HeaderCarrier, _: ExecutionContext)).expects(*, *, *, *)
//        .returning(Future.successful(Some(Credentials("test", "test"))))
//
//      (mockValidator.validate(_: PersonalDetails, _: Option[String], _: Option[String])(_: HeaderCarrier, _: Request[_], _: ExecutionContext))
//        .expects(personalDetails, origin, maybeCredId, instanceOf[HeaderCarrier], requestWithBody, *)
//        .returns(EitherT.leftT[Future, PersonalDetailsValidation](badGatewayException))
//
//      await(controller.create(origin)(requestWithBody).failed) shouldBe badGatewayException
//    }
//
//    "return BAD_REQUEST if postcode is supplied in an incorrect format" in new Setup {
//      val personalDetailsWithNino: PersonalDetailsWithNino = randomPersonalDetails.asInstanceOf[PersonalDetailsWithNino]
//      val personalDetails: PersonalDetailsWithPostCode = PersonalDetailsWithPostCode(
//        personalDetailsWithNino.firstName,
//        personalDetailsWithNino.lastName,
//        personalDetailsWithNino.dateOfBirth,
//        "INVALID POSTCODE"
//      )
//
//      val response: Future[Result] = controller.create(origin)(request.withBody(personalDetails.toJson))
//
//      status(response) shouldBe BAD_REQUEST
//    }
//
//    "return BAD_REQUEST if mandatory fields are missing" in new Setup {
//      val response: Future[Result] = controller.create(origin)(request.withBody(JsNull))
//
//      status(response) shouldBe BAD_REQUEST
//    }
//
//
//    "return bad request if mandatory fields are missing" in new Setup {
//      val response: Result = controller.create(origin)(request.withBody(JsNull)).futureValue
//
//      status(response) shouldBe BAD_REQUEST
//
//    }
//
//    val invalidDataScenarios = Table(
//      ("scenario", "requestPersonalDetails"),
//      ("firstName is empty", Json.obj("firstName" -> JsString(""))),
//      ("firstName is blank", Json.obj("firstName" -> JsString("   "))),
//      ("lastName is empty", Json.obj("lastName" -> JsString(""))),
//      ("lastName is blank", Json.obj("lastName" -> JsString("  "))),
//      ("dateOfBirth is in invalid iso format", Json.obj("dateOfBirth" -> JsString("31/12/2018"))),
//      ("dateOfBirth is an invalid date", Json.obj("dateOfBirth" -> JsString("2018-11-31"))),
//      ("nino is invalid", Json.obj("nino" -> JsString(" 1234 "))),
//      ("nino and postCode both supplied", Json.obj("postCode" -> JsString("SE1 9NT"))),
//      ("at least one of nino or postCode supplied", Json.obj("nino" -> JsNull)),
//      ("multiple data invalid", Json.obj("nino" -> JsString(" 1234 "), "firstName" -> JsString("")))
//    )
//
//    forAll(invalidDataScenarios) { (scenario, jsonModification) =>
//      s"return errors if $scenario" in new Setup {
//        val personalDetailsJson: JsObject = Json.toJson(randomPersonalDetails).as[JsObject] ++ jsonModification
//        val response: Result = controller.create(origin)(request.withBody(personalDetailsJson)).futureValue
//
//        status(response) shouldBe BAD_REQUEST
//
//      }
//    }
//  }
//
//  "Get in PersonalDetailsValidationResourceController" should {
//
//    implicit val generator: Arbitrary[PersonalDetailsValidation] = asArbitrary(personalDetailsValidationObjects)
//
//    "return Not Found http status code if repository does not return personal details validation" in new Setup {
//      (uuidProvider.apply _).expects().returning(randomUUID)
//      val validationId: ValidationId = ValidationId()
//      (mockPersonalDetailsValidatorService.getRecord(_: ValidationId)(_: ExecutionContext))
//        .expects(validationId, *)
//        .returns(Future.successful(None))
//
//      val response: Result = controller.get(validationId)(request).futureValue
//
//      status(response) shouldBe NOT_FOUND
//    }
//
//    "return OK http status code if repository returns personal details validation" in new Setup {
//      forAll { personalDetailsValidation: PersonalDetailsValidation =>
//        (mockPersonalDetailsValidatorService.getRecord(_: ValidationId)(_: ExecutionContext))
//          .expects(personalDetailsValidation.id, *)
//          .returns(Future.successful(Some(personalDetailsValidation)))
//
//        val response = controller.get(personalDetailsValidation.id)(request).futureValue
//
//        status(response) shouldBe OK
//      }
//    }
//
//    "return personalDetailsValidation id in response body" in new Setup {
//      forAll { personalDetailsValidation: PersonalDetailsValidation =>
//        (mockPersonalDetailsValidatorService.getRecord(_: ValidationId)(_: ExecutionContext))
//          .expects(personalDetailsValidation.id, *)
//          .returns(Future.successful(Some(personalDetailsValidation)))
//
//        val response = controller.get(personalDetailsValidation.id)(request).futureValue
//
//        (jsonBodyOf(response) \ "id").as[String] shouldBe personalDetailsValidation.id.value.toString
//      }
//    }
//
//    "return personal details in response body for SuccessfulPersonalDetailsValidation" in new Setup {
//
//      import formats.PersonalDetailsFormat._
//
//      forAll { personalDetailsValidation: SuccessfulPersonalDetailsValidation =>
//        (mockPersonalDetailsValidatorService.getRecord(_: ValidationId)(_: ExecutionContext))
//          .expects(personalDetailsValidation.id, *)
//          .returns(Future.successful(Some(personalDetailsValidation)))
//
//        val response = controller.get(personalDetailsValidation.id)(request).futureValue
//
//        (jsonBodyOf(response) \ "personalDetails").as[PersonalDetails] shouldBe personalDetailsValidation.personalDetails
//      }
//    }
//
//    "do not return personal details in response body for FailedPersonalDetailsValidation" in new Setup {
//
//      forAll { personalDetailsValidation: FailedPersonalDetailsValidation =>
//        (mockPersonalDetailsValidatorService.getRecord(_: ValidationId)(_: ExecutionContext))
//          .expects(personalDetailsValidation.id, *)
//          .returns(Future.successful(Some(personalDetailsValidation)))
//
//        val response = controller.get(personalDetailsValidation.id)(request).futureValue
//
//        (jsonBodyOf(response) \ "personalDetails") shouldBe a[JsUndefined]
//      }
//    }
//
//    val validationStatusScenarios = Table(
//      ("personalDetailsValidation", "result"),
//      (successfulPersonalDetailsValidationObjects.generateOne, "success"),
//      (failedPersonalDetailsValidationObjects.generateOne, "failure")
//    )
//
//    forAll(validationStatusScenarios) { (personalDetailsValidation, status) =>
//      s"return validationStatus as $status if repository returned ${personalDetailsValidation.getClass.getSimpleName}" in new Setup {
//        (mockPersonalDetailsValidatorService.getRecord(_: ValidationId)(_: ExecutionContext))
//          .expects(personalDetailsValidation.id, *)
//          .returns(Future.successful(Some(personalDetailsValidation)))
//
//        val response: Result = controller.get(personalDetailsValidation.id)(request).futureValue
//
//        (jsonBodyOf(response) \ "validationStatus").as[String] shouldBe status
//      }
//    }
//  }

    trait Setup {

    implicit val materializer: Materializer = app.materializer
    implicit val uuidProvider: UUIDProvider = mock[UUIDProvider]

    val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    val mockPersonalDetailsValidatorService: PersonalDetailsValidatorService = mock[PersonalDetailsValidatorService]

    val personalDetailsValidationMongoRepositoryConfig: PersonalDetailsValidationMongoRepositoryConfig =
      app.injector.instanceOf[PersonalDetailsValidationMongoRepositoryConfig]

    val mongoComponent: MongoComponent = app.injector.instanceOf[MongoComponent]

    val personalDetailsValidationRetryRepository: PersonalDetailsValidationRetryRepository =
      new PersonalDetailsValidationRetryRepository(personalDetailsValidationMongoRepositoryConfig, mongoComponent)

    val mockValidator: PersonalDetailsValidator = mock[PersonalDetailsValidator]
    val origin: Some[String] = Some("Test")
    val maybeCredId: Some[String] = Some("test")
    implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]

    val controller = new PersonalDetailsValidationResourceController(
      mockPersonalDetailsValidatorService, personalDetailsValidationRetryRepository, mockValidator, stubControllerComponents())
  }

}
