package uk.gov.hmrc.personaldetailsvalidation

import ch.qos.logback.classic.Level
import org.scalatest.LoneElement
import play.api.Logger
import play.api.http.ContentTypes.JSON
import play.api.http.Status._
import play.api.libs.json.{JsUndefined, JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.test.DefaultAwaitTimeout
import play.mvc.Http.HeaderNames.{CONTENT_TYPE, LOCATION}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, PersonalDetailsWithNino, SuccessfulPersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.personaldetailsvalidation.services.Encryption
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.support.BaseIntegrationSpec
import uk.gov.hmrc.support.stubs.AuditEventStubs._
import uk.gov.hmrc.support.stubs.PlatformAnalyticsStub._
import uk.gov.hmrc.support.stubs.{AuthStub, AuthenticatorStub, CitizenDetailsStub}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PersonalDetailsValidationISpec
  extends BaseIntegrationSpec
    with DefaultAwaitTimeout
    with LogCapturing
    with LoneElement {

  "POST /personal-details-validation" should {

    "return OK with success validation status when provided personal details can be matched by Authenticator" in new Setup {

      AuthenticatorStub.expecting(personalDetails).respondWithOK()
      CitizenDetailsStub.expecting().respondWithOK()

      val createResponse: WSResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe CREATED

      val Some(resourceUrl) = createResponse.header(LOCATION)
      val validationId: String = resourceUrl.substring(resourceUrl.lastIndexOf("/") + 1)

      (createResponse.json \ "id").as[String] mustBe validationId
      (createResponse.json \ "validationStatus").as[String] mustBe "success"
      (createResponse.json \ "personalDetails").as[JsValue] mustBe Json.parse(personalDetails)

      val getResponse: WSResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK

      (getResponse.json \ "id").as[String] mustBe validationId
      (getResponse.json \ "validationStatus").as[String] mustBe "success"
      (getResponse.json \ "personalDetails").as[JsValue] mustBe Json.parse(personalDetails)

      verifyGAMatchEvent(label = "success")
      verifyGAMatchEvent(label = "success_withNINO")
      verifyMatchingStatusInAuditEvent(matchingStatus = "success")
    }

    "return OK and insert records into both databases when provided personal details can be matched by Authenticator" in new Setup {
      AuthenticatorStub.expecting(personalDetails).respondWithOK()
      CitizenDetailsStub.expecting().respondWithOK()
      AuthStub.stubForAuth(200)

      val createResponse1: Future[WSResponse] = sendCreateValidationResourceRequest(personalDetails, headers)
      createResponse1.map(
        response => response.status mustBe CREATED
      )

      eventually {
        associationRepository.getRecord(encryptedCredID, encryptedSessionID).futureValue.nonEmpty mustBe true
      }

      val storedPDV: Future[Option[PersonalDetailsValidation]] = eventually(pdvRepository.get(repoValidationId))
      storedPDV.map { value =>
        value.nonEmpty mustBe true
        value.get.id mustBe repoValidationId
        value.get mustBe personalDetailsValidation
      }
    }

    "return ok if records already exist before hitting the routes" in new Setup {
      AuthenticatorStub.expecting(personalDetails).respondWithOK()
      CitizenDetailsStub.expecting().respondWithOK()
      AuthStub.stubForAuth(200)

      val createResponse1: Future[WSResponse] = sendCreateValidationResourceRequest(personalDetails, headers)
        createResponse1.map(
          response => response.status mustBe CREATED
        )

      val createResponse2: WSResponse = sendCreateValidationResourceRequest(personalDetails, headers).futureValue
      createResponse2.status mustBe CREATED
      val responseID: String = (createResponse2.json \ "id").as[String]

      eventually {
        associationRepository.getRecord(encryptedCredID, encryptedSessionID).futureValue.nonEmpty mustBe true
      }

      eventually {
        pdvRepository
          .get(ValidationId(UUID.fromString(responseID)))
          .map {
            case Some(SuccessfulPersonalDetailsValidation(_, _, personalDetails, _, _)) => Some(personalDetails)
            case _ => None
          }
          .futureValue mustBe Some(personalDetailsValidation.personalDetails)
      }
    }

    "return OK and insert in to PDV repo but not association repo when missing session id" in new Setup {
      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService")) { logEvents =>
        AuthenticatorStub.expecting(personalDetails).respondWithOK()
        CitizenDetailsStub.expecting().respondWithOK()
        AuthStub.stubForAuth(200)

        val createResponse1: Future[WSResponse] = sendCreateValidationResourceRequest(personalDetails, headersMissingSessionId)
        createResponse1.map(
          response => response.status mustBe CREATED
        )

        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage mustBe "adding to Association database rejected due to sessionID does not exist"
        }

        val storedPDV: Future[Option[PersonalDetailsValidation]] = eventually(pdvRepository.get(repoValidationId))
        storedPDV.map { value =>
          value.nonEmpty mustBe true
          value.get.id mustBe repoValidationId
          value.get mustBe personalDetailsValidation
        }
      }
    }

    "return OK and insert in to PDV repo but not association repo when session id is empty" in new Setup {
      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService")) { logEvents =>

        AuthenticatorStub.expecting(personalDetails).respondWithOK()
        CitizenDetailsStub.expecting().respondWithOK()
        AuthStub.stubForAuth(200)

        val createResponse1: Future[WSResponse] = sendCreateValidationResourceRequest(personalDetails, headersEmptySessionId)
        createResponse1.map(
          response => response.status mustBe CREATED
        )

        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage mustBe "adding to Association database rejected due to sessionID containing empty string"
        }

        val storedPDV: Future[Option[PersonalDetailsValidation]] = eventually(pdvRepository.get(repoValidationId))
        storedPDV.map { value =>
          value.nonEmpty mustBe true
          value.get.id mustBe repoValidationId
          value.get mustBe personalDetailsValidation
        }
      }
    }

    "return OK and insert in to PDV repo but not association repo when cred id is none" in new Setup {
      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService")) { logEvents =>

        AuthenticatorStub.expecting(personalDetails).respondWithOK()
        CitizenDetailsStub.expecting().respondWithOK()
        AuthStub.stubForAuth(200, AuthStub.authBodyNoCredID)

        val createResponse1: Future[WSResponse] = sendCreateValidationResourceRequest(personalDetails, headers)
        createResponse1.map(
          response => response.status mustBe CREATED
        )

        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage mustBe "adding to Association database rejected due to credID does not exist"
        }

        val storedPDV: Future[Option[PersonalDetailsValidation]] = eventually(pdvRepository.get(repoValidationId))
        storedPDV.map { value =>
          value.nonEmpty mustBe true
          value.get.id mustBe repoValidationId
          value.get mustBe personalDetailsValidation
        }
      }
    }

    "return OK and insert in to PDV repo but not association repo when cred id is empty" in new Setup {
      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService")) { logEvents =>

        AuthenticatorStub.expecting(personalDetails).respondWithOK()
        CitizenDetailsStub.expecting().respondWithOK()
        AuthStub.stubForAuth(200, AuthStub.authBody(""))

        val createResponse1: Future[WSResponse] = sendCreateValidationResourceRequest(personalDetails, headers)
        createResponse1.map(
          response => response.status mustBe CREATED
        )

        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage mustBe "adding to Association database rejected due to credID containing empty string"
        }

        val storedPDV: Future[Option[PersonalDetailsValidation]] = eventually(pdvRepository.get(repoValidationId))
        storedPDV.map { value =>
          value.nonEmpty mustBe true
          value.get.id mustBe repoValidationId
          value.get mustBe personalDetailsValidation
        }
      }
    }

    "return OK with success validation status when provided personal details, that contain postcode, can be matched by Authenticator. Include nino in response" in new Setup {

      AuthenticatorStub.expecting(personalDetailsWithPostCode).respondWithOK()
      CitizenDetailsStub.expecting().respondWithOK()

      val createResponse: WSResponse = sendCreateValidationResourceRequest(personalDetailsWithPostCode).futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)

      val getResponse: WSResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK

      val validationId: String = resourceUrl.substring(resourceUrl.lastIndexOf("/") + 1)
      (getResponse.json \ "id").as[String] mustBe validationId
      (getResponse.json \ "validationStatus").as[String] mustBe "success"
      (getResponse.json \ "personalDetails").as[JsValue] mustBe Json.parse(personalDetailsWithBothNinoAndPostcode)

      verifyGAMatchEvent(label = "success")
      verifyGAMatchEvent(label = "success_withPOSTCODE")
      verifyMatchingStatusInAuditEvent(matchingStatus = "success")
    }

    "return OK with failure validation status when provided personal details cannot be matched by Authenticator" in new Setup {

      val failureDetail = "Last Name does not match CID"
      AuthenticatorStub.expecting(personalDetails).respondWith(UNAUTHORIZED, Some(Json.obj("errors" -> failureDetail)))

      val createResponse: WSResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)
      val validationId: String = resourceUrl.substring(resourceUrl.lastIndexOf("/") + 1)

      (createResponse.json \ "id").as[String] mustBe validationId
      (createResponse.json \ "validationStatus").as[String] mustBe "failure"
      (createResponse.json \ "personalDetails") mustBe a[JsUndefined]

      val getResponse: WSResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK

      (getResponse.json \ "id").as[String] mustBe validationId
      (getResponse.json \ "validationStatus").as[String] mustBe "failure"
      (getResponse.json \ "personalDetails") mustBe a[JsUndefined]

      verifyGAMatchEvent(label = "failed_matching")
      verifyGAMatchEvent(label = "failed_matching_withNINO")
      verifyMatchingStatusInAuditEvent(matchingStatus = "failed")
      verifyFailureDetailInAuditEvent(failureDetail = failureDetail)
    }

    "return BAD_GATEWAY when Authenticator returns an unexpected status code" in new Setup {
      AuthenticatorStub.expecting(personalDetails).respondWith(NO_CONTENT)

      val createResponse: WSResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe BAD_GATEWAY

      verifyGAMatchEvent("technical_error_matching")
      verifyMatchingStatusInAuditEvent(matchingStatus = "technicalError")
    }

    "return BAD Request if mandatory fields are missing" in new Setup {
      val createResponse: WSResponse = sendCreateValidationResourceRequest("{}").futureValue

      createResponse.status mustBe BAD_REQUEST

      (createResponse.json \ "errors").as[List[String]] must contain only(
        "firstName is missing",
        "lastName is missing",
        "dateOfBirth is missing/invalid",
        "at least nino or postcode needs to be supplied"
      )
    }

    "return BAD Request if both nino and postcode are supplied" in new Setup {
      val createResponse: WSResponse = sendCreateValidationResourceRequest(personalDetailsWithBothNinoAndPostcode).futureValue

      createResponse.status mustBe BAD_REQUEST

      (createResponse.json \ "errors").as[List[String]] must contain only "both nino and postcode supplied"
    }

    "return BAD Request if neither nino or postcode are supplied" in new Setup {
      val createResponse: WSResponse = sendCreateValidationResourceRequest(invalidPersonalDetailsWithNeitherPostcodeOrNino).futureValue

      createResponse.status mustBe BAD_REQUEST

      (createResponse.json \ "errors").as[List[String]] must contain only "at least nino or postcode needs to be supplied"
    }
  }

  "GET /personal-details-validation/id" should {

    "return NOT FOUND if id is UUID but invalid id" in {
      val getResponse = wsUrl(s"/personal-details-validation/${randomUUID().toString}").get().futureValue
      getResponse.status mustBe NOT_FOUND
    }

    "return NOT FOUND if id is not a valid UUID" in {
      val getResponse = wsUrl(s"/personal-details-validation/foo-bar").get().futureValue
      getResponse.status mustBe NOT_FOUND
    }
  }

  private trait Setup {
    private val testSessionId: String = s"session-${UUID.randomUUID().toString}"
    val headers: List[(String, String)] = List(("X-Session-ID", testSessionId), ("Authorization" , "Bearer123"))
    val headersMissingSessionId: List[(String, String)] = List(("Authorization" , "Bearer123"))
    val headersEmptySessionId: List[(String, String)] = List(("X-Session-ID", ""), ("Authorization" , "Bearer123"))

    val associationRepository: AssociationMongoRepository = app.injector.instanceOf[AssociationMongoRepository]
    val pdvRepository: PersonalDetailsValidationRepository = app.injector.instanceOf[PersonalDetailsValidationRepository]

    private val dateFormat: String = "yyyy-MM-dd HH:mm:ss.SSSSSS"
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat)
    val lastUpdated: LocalDateTime = LocalDateTime.now()
    val repoValidationId: ValidationId = ValidationId(UUID.randomUUID())

    implicit val encryption: Encryption = app.injector.instanceOf[Encryption]
    val encryptedCredID: String = encryption.crypto.encrypt(PlainText(AuthStub.credId)).value
    val encryptedSessionID: String = encryption.crypto.encrypt(PlainText(testSessionId)).value
    val testPersonalDetails: PersonalDetailsWithNino = PersonalDetailsWithNino("Jim","Ferguson",LocalDate.parse("1948-04-23 12:00:00.000000", formatter),Nino("AA000003D"))
    val personalDetailsValidation: SuccessfulPersonalDetailsValidation = SuccessfulPersonalDetailsValidation(repoValidationId,"success", testPersonalDetails, lastUpdated)


    val personalDetails: String =
      """
        |{
        |   "firstName": "Jim",
        |   "lastName": "Ferguson",
        |   "nino": "AA000003D",
        |   "dateOfBirth": "1948-04-23"
        |}
      """.stripMargin

    val personalDetailsWithPostCode: String =
      """
        |{
        |   "firstName": "Jim",
        |   "lastName": "Ferguson",
        |   "postCode": "SE1 9NT",
        |   "dateOfBirth": "1948-04-23"
        |}
      """.stripMargin

    val personalDetailsWithBothNinoAndPostcode: String =
      """
        |{
        |   "firstName": "Jim",
        |   "lastName": "Ferguson",
        |   "nino": "AA000003D",
        |   "postCode": "SE1 9NT",
        |   "dateOfBirth": "1948-04-23"
        |}
      """.stripMargin

    val invalidPersonalDetailsWithNeitherPostcodeOrNino: String =
      """
        |{
        |   "firstName": "Jim",
        |   "lastName": "Ferguson",
        |   "dateOfBirth": "1948-04-23"
        |}
      """.stripMargin

    def sendCreateValidationResourceRequest(body: String, headers: List[(String,String)]= List.empty): Future[WSResponse] =
      wsUrl("/personal-details-validation")
        .addHttpHeaders(CONTENT_TYPE -> JSON)
        .addHttpHeaders(headers: _*)
        .post(body)
  }

}
