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

import cats.data.EitherT
import factory.ObjectFactory.*
import generators.Generators.Implicits.*
import generators.ObjectGenerators.*
import org.apache.pekko.stream.Materializer
import org.bson.BsonDocument
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, verifyNoInteractions, when}
import org.scalacheck.Arbitrary
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.mongodb.scala.SingleObservableFuture
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.*
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.personaldetailsvalidation.*
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat.personalDetailsValidationFormats
import uk.gov.hmrc.personaldetailsvalidation.mocks.MockValidator
import uk.gov.hmrc.personaldetailsvalidation.mocks.connectors.MockAuthConnector
import uk.gov.hmrc.personaldetailsvalidation.mocks.services.MockPdvService
import uk.gov.hmrc.personaldetailsvalidation.model.*
import uk.gov.hmrc.uuid.UUIDProvider

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PersonalDetailsValidationResourceControllerSpec
  extends AnyWordSpec
    with UnitSpec
    with ScalaCheckPropertyChecks
    with ScalaFutures
    with Matchers
    with GuiceOneAppPerSuite
    with TableDrivenPropertyChecks
    with CommonTestData
    with BeforeAndAfterEach {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(Map("metrics.enabled" -> "false"))
      .build()

  implicit val materializer: Materializer = app.materializer
  implicit val uuidProvider: UUIDProvider = mock[UUIDProvider]

  val personalDetailsValidationMongoRepositoryConfig: PersonalDetailsValidationMongoRepositoryConfig =
    app.injector.instanceOf[PersonalDetailsValidationMongoRepositoryConfig]

  val mongoComponent: MongoComponent = app.injector.instanceOf[MongoComponent]

  val personalDetailsValidationRetryRepository: PersonalDetailsValidationRetryRepository =
    new PersonalDetailsValidationRetryRepository(personalDetailsValidationMongoRepositoryConfig, mongoComponent)

  override implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]

  val controller = new PersonalDetailsValidationResourceController(
    mockPersonalDetailsValidatorService,
    personalDetailsValidationRetryRepository,
    mockValidator,
    stubControllerComponents()
  )

  val mockRetryRepository: PersonalDetailsValidationRetryRepository = mock[PersonalDetailsValidationRetryRepository]

  val controllerWithMockRetryRepository = new PersonalDetailsValidationResourceController(
    mockPersonalDetailsValidatorService,
    mockRetryRepository,
    mockValidator,
    stubControllerComponents()
  )

  def stubAuthFailure(exception: Exception): Unit =
    when(mockAuthConnector.authorise[Option[Credentials]](
      any[Predicate],
      any[Retrieval[Option[Credentials]]]
    )(using any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.failed(exception))

  override def beforeEach(): Unit = {
    reset(mockPersonalDetailsValidatorService, mockValidator)
    super.beforeEach()
  }

  "create" should {

    implicit val personalDetailsGenerator: Arbitrary[PersonalDetails] = asArbitrary(using personalDetailsObjects)
    implicit val personalDetailsValidationGenerator: Arbitrary[PersonalDetailsValidation] = asArbitrary(using personalDetailsValidationObjects)

    "return CREATED when personal details are validated with no error" in {
      forAll { (personalDetails: PersonalDetails, personalDetailsValidation: PersonalDetailsValidation) =>
        val requestWithBody = request.withBody(toJson(personalDetails))

        MockAuthConnector.authoriseSuccess(mockAuthConnector)
        MockValidator.validate(mockValidator, personalDetails, testOrigin, testMaybeCredId)(personalDetailsValidation)

        val response = controller.create(testOrigin)(requestWithBody)

        status(response) shouldBe CREATED
      }
    }

    val ninoTransformationScenarios: TableFor3[String, String, String] = Table(
      ("scenario", "originalNinoValue", "finalNinoValue"),
      ("convert nino value to UPPER CASE", "aa000003d", "AA000003D"),
      ("remove intermediate spaces in nino value", "aa 00 00 03 d", "AA000003D")
    )

    forAll(ninoTransformationScenarios) { (scenario, originalNinoValue, finalNinoValue) =>
      s"$scenario" in {
        val personalDetailsValidation: PersonalDetailsValidation = personalDetailsValidationObjects.generateOne
        val requestPersonalDetails: PersonalDetailsWithNino = randomPersonalDetails.asInstanceOf[PersonalDetailsWithNino]
        val json: JsObject =
          Json.toJson[PersonalDetails](requestPersonalDetails).as[JsObject] + ("nino" -> JsString(originalNinoValue))
        val requestWithBody: FakeRequest[JsObject] = request.withBody(json)

        val personalDetailsWithUpperCaseNino: PersonalDetailsWithNino = requestPersonalDetails.copy(nino = Nino(finalNinoValue))

        MockAuthConnector.authoriseSuccess(mockAuthConnector)
        MockValidator.validate(mockValidator, personalDetailsWithUpperCaseNino, testOrigin, testMaybeCredId)(personalDetailsValidation)

        val response: Future[Result] = controller.create(testOrigin)(requestWithBody)

        status(response) shouldBe CREATED
      }
    }

    "return uri of the personal-details-validation/:id in the response Location header" in {
      forAll { (personalDetails: PersonalDetails) =>
        val requestWithBody = request.withBody(toJson(personalDetails))

        MockAuthConnector.authoriseSuccess(mockAuthConnector)
        MockValidator.validate(mockValidator, personalDetails, testOrigin, testMaybeCredId)(personalDetailsValidation)

        val response = controller.create(testOrigin)(requestWithBody)

        header(LOCATION, response) shouldBe Some(controllers.routes.PersonalDetailsValidationResourceController.get(personalDetailsValidation.id).url)
      }
    }

    "return validation response of the personal-details-validation/:id in the response body" in {
      forAll { (personalDetails: PersonalDetails, personalDetailsValidation: PersonalDetailsValidation) =>
        val requestWithBody = request.withBody(toJson(personalDetails))

        MockAuthConnector.authoriseSuccess(mockAuthConnector)
        MockValidator.validateNoId(mockValidator, personalDetailsValidation, testOrigin)

        val response = controller.create(testOrigin)(requestWithBody)

        status(response) shouldBe CREATED
        contentAsJson(response) shouldBe toJson(personalDetailsValidation)

      }
    }

    "return BadGatewayException if matching error occurs" in {
      val personalDetails: PersonalDetails      = randomPersonalDetails
      val requestWithBody: FakeRequest[JsValue] = request.withBody(toJson(personalDetails))

      MockAuthConnector.authoriseSuccess(mockAuthConnector)
      MockValidator.validateException(mockValidator, personalDetails, testOrigin, testMaybeCredId)(badGatewayException)

      await(controller.create(testOrigin)(requestWithBody).failed) shouldBe badGatewayException
    }

    "treat a missing bearer token as a no-credId journey and continue validation" in {
      val personalDetails: PersonalDetails = randomPersonalDetails
      val requestWithBody                  = request.withBody(toJson(personalDetails))

      stubAuthFailure(new MissingBearerToken())
      MockValidator.validate(mockValidator, personalDetails, testOrigin, None)(personalDetailsValidation)

      status(controller.create(testOrigin)(requestWithBody)) shouldBe CREATED
    }

    "fail closed without validating when auth returns an unexpected error" in {
      val personalDetails: PersonalDetails = randomPersonalDetails
      val requestWithBody                  = request.withBody(toJson(personalDetails))

      stubAuthFailure(UpstreamErrorResponse("auth outage", 500))

      await(controller.create(testOrigin)(requestWithBody).failed) shouldBe a[UpstreamErrorResponse]
      verifyNoInteractions(mockValidator)
    }

    "return BAD_REQUEST if postcode is supplied in an incorrect format" in {
      val personalDetailsWithNino: PersonalDetailsWithNino = randomPersonalDetails.asInstanceOf[PersonalDetailsWithNino]
      val personalDetails: PersonalDetailsWithPostCode = PersonalDetailsWithPostCode(
        personalDetailsWithNino.firstName,
        personalDetailsWithNino.lastName,
        personalDetailsWithNino.dateOfBirth,
        "INVALID POSTCODE"
      )

      val response: Future[Result] = controller.create(testOrigin)(request.withBody(personalDetails.toJson))

      status(response) shouldBe BAD_REQUEST
    }

    "return BAD_REQUEST if mandatory fields are missing" in {
      val response: Future[Result] = controller.create(testOrigin)(request.withBody(JsNull))

      status(response) shouldBe BAD_REQUEST
    }


    "return bad request if mandatory fields are missing" in {
      val response: Result = controller.create(testOrigin)(request.withBody(JsNull)).futureValue

      status(response) shouldBe BAD_REQUEST

    }

    val invalidDataScenarios = Table(
      ("scenario", "requestPersonalDetails"),
      ("firstName is empty", Json.obj("firstName" -> JsString(""))),
      ("firstName is blank", Json.obj("firstName" -> JsString("   "))),
      ("lastName is empty", Json.obj("lastName" -> JsString(""))),
      ("lastName is blank", Json.obj("lastName" -> JsString("  "))),
      ("dateOfBirth is in invalid iso format", Json.obj("dateOfBirth" -> JsString("31/12/2018"))),
      ("dateOfBirth is an invalid date", Json.obj("dateOfBirth" -> JsString("2018-11-31"))),
      ("nino is invalid", Json.obj("nino" -> JsString(" 1234 "))),
      ("nino and postCode both supplied", Json.obj("postCode" -> JsString("SE1 9NT"))),
      ("at least one of nino or postCode supplied", Json.obj("nino" -> JsNull)),
      ("multiple data invalid", Json.obj("nino" -> JsString(" 1234 "), "firstName" -> JsString("")))
    )

    forAll(invalidDataScenarios) { (scenario, jsonModification) =>
      s"return errors if $scenario" in {
        val personalDetailsJson: JsObject = Json.toJson(randomPersonalDetails).as[JsObject] ++ jsonModification
        val response: Result = controller.create(testOrigin)(request.withBody(personalDetailsJson)).futureValue

        status(response) shouldBe BAD_REQUEST

      }
    }
  }

  "getUserAttempts" should {

    "return the attempts recorded for the authenticated credId" in {
      await(personalDetailsValidationRetryRepository.collection.deleteMany(new BsonDocument()).toFuture())
      MockAuthConnector.authoriseSuccess(mockAuthConnector)
      await(personalDetailsValidationRetryRepository.incrementAttempts("test"))

      val response: Result = controller.getUserAttempts(request).futureValue

      status(response) shouldBe OK
      (jsonBodyOf(response) \ "attempts").as[Int] shouldBe 1
      (jsonBodyOf(response) \ "maybeCredId").as[String] shouldBe "test"
    }

    "return zero attempts and no credId when there is no session" in {
      stubAuthFailure(new MissingBearerToken())

      val response: Result = controller.getUserAttempts(request).futureValue

      status(response) shouldBe OK
      (jsonBodyOf(response) \ "attempts").as[Int] shouldBe 0
      (jsonBodyOf(response) \ "maybeCredId") shouldBe a[JsUndefined]
    }

    "fail when the retry store cannot be read" in {
      MockAuthConnector.authoriseSuccess(mockAuthConnector)
      when(mockRetryRepository.getAttemptsT(any[Option[String]])(any[ExecutionContext]))
        .thenReturn(EitherT.leftT[Future, Int](exception))

      val response = controllerWithMockRetryRepository.getUserAttempts(request)

      await(response.failed) shouldBe a[RuntimeException]
    }
  }

  "Get in PersonalDetailsValidationResourceController" should {

    implicit val generator: Arbitrary[PersonalDetailsValidation] = asArbitrary(using personalDetailsValidationObjects)

    "return Not Found http status code if repository does not return personal details validation" in {
      when(uuidProvider.apply()).thenReturn(randomUUID)

      val validationId: ValidationId = ValidationId()

      MockPdvService.getRecord(mockPersonalDetailsValidatorService, validationId)(None)

      val response: Result = controller.get(validationId)(request).futureValue

      status(response) shouldBe NOT_FOUND
    }

    "return OK http status code if repository returns personal details validation" in {
      forAll { (personalDetailsValidation: PersonalDetailsValidation) =>
        MockPdvService.getRecord(mockPersonalDetailsValidatorService, personalDetailsValidation.id)(Some(personalDetailsValidation))

        val response = controller.get(personalDetailsValidation.id)(request).futureValue

        status(response) shouldBe OK
      }
    }

    "return personalDetailsValidation id in response body" in {
      forAll { (personalDetailsValidation: PersonalDetailsValidation) =>
        MockPdvService.getRecord(mockPersonalDetailsValidatorService, personalDetailsValidation.id)(Some(personalDetailsValidation))

        val response = controller.get(personalDetailsValidation.id)(request).futureValue

        (jsonBodyOf(response) \ "id").as[String] shouldBe personalDetailsValidation.id.value.toString
      }
    }

    "return personal details in response body for SuccessfulPersonalDetailsValidation" in {

      import formats.PersonalDetailsFormat.*

      forAll { (personalDetailsValidation: SuccessfulPersonalDetailsValidation) =>
        MockPdvService.getRecord(mockPersonalDetailsValidatorService, personalDetailsValidation.id)(Some(personalDetailsValidation))

        val response = controller.get(personalDetailsValidation.id)(request).futureValue

        (jsonBodyOf(response) \ "personalDetails").as[PersonalDetails] shouldBe personalDetailsValidation.personalDetails
      }
    }

    "do not return personal details in response body for FailedPersonalDetailsValidation" in {

      forAll { (personalDetailsValidation: FailedPersonalDetailsValidation) =>
        MockPdvService.getRecord(mockPersonalDetailsValidatorService, personalDetailsValidation.id)(Some(personalDetailsValidation))

        val response = controller.get(personalDetailsValidation.id)(request).futureValue

        (jsonBodyOf(response) \ "personalDetails") shouldBe a[JsUndefined]
      }
    }

    val validationStatusScenarios = Table(
      ("personalDetailsValidation", "result"),
      (personalDetailsValidationSuccess, "success"),
      (personalDetailsValidationFailure, "failure")
    )

    forAll(validationStatusScenarios) { (personalDetailsValidation, status) =>
      s"return validationStatus as $status if repository returned ${personalDetailsValidation.getClass.getSimpleName}" in {
        MockPdvService.getRecord(mockPersonalDetailsValidatorService, personalDetailsValidation.id)(Some(personalDetailsValidation))

        val response: Result = controller.get(personalDetailsValidation.id)(request).futureValue

        (jsonBodyOf(response) \ "validationStatus").as[String] shouldBe status
      }
    }
  }

}
