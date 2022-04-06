package uk.gov.hmrc.personaldetailsvalidation

import play.api.http.ContentTypes.JSON
import play.api.http.Status._
import play.api.libs.json.{JsUndefined, JsValue, Json}
import play.api.libs.ws.WSResponse
import play.mvc.Http.HeaderNames.{CONTENT_TYPE, LOCATION}
import uk.gov.hmrc.support.BaseIntegrationSpec
import uk.gov.hmrc.support.stubs.AuditEventStubs._
import uk.gov.hmrc.support.stubs.AuthenticatorStub
import uk.gov.hmrc.support.stubs.PlatformAnalyticsStub._

import java.util.UUID.randomUUID
import scala.concurrent.Future

class PersonalDetailsValidationISpec extends BaseIntegrationSpec {

  "POST /personal-details-validation" should {

    "return OK with success validation status when provided personal details can be matched by Authenticator" in new Setup {

      AuthenticatorStub.expecting(personalDetails).respondWithOK()

      val createResponse: WSResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe CREATED

      val Some(resourceUrl) = createResponse.header(LOCATION)
      val validationId: String = resourceUrl.substring(resourceUrl.lastIndexOf("/") + 1)

      (createResponse.json \ "id").as[String] mustBe validationId
      (createResponse.json \ "validationStatus").as[String] mustBe "success"
      (createResponse.json \ "personalDetails").as[JsValue] mustBe Json.parse(personalDetails)

      val getResponse: WSResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK

      (getResponse.json \\ "id").head.as[String] mustBe validationId
      (getResponse.json \\ "validationStatus").head.as[String] mustBe "success"
      (getResponse.json \\ "personalDetails").head.as[JsValue] mustBe Json.parse(personalDetails)

      verifyGAMatchEvent(label = "success")
      verifyGAMatchEvent(label = "success_withNINO")
      verifyMatchingStatusInAuditEvent(matchingStatus = "success")
    }

    "return OK with success validation status when provided personal details, that contain postcode, can be matched by Authenticator. Include nino in response" in new Setup {

      AuthenticatorStub.expecting(personalDetailsWithPostCode).respondWithOK()

      val createResponse: WSResponse = sendCreateValidationResourceRequest(personalDetailsWithPostCode).futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)

      val getResponse: WSResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK

      val validationId: String = resourceUrl.substring(resourceUrl.lastIndexOf("/") + 1)

      (getResponse.json \\ "id").head.as[String] mustBe validationId
      (getResponse.json \\ "validationStatus").head.as[String] mustBe "success"
      (getResponse.json \\ "personalDetails").head.as[JsValue] mustBe Json.parse(personalDetailsWithBothNinoAndPostcode)

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

      (getResponse.json \\ "id").head.as[String] mustBe validationId
      (getResponse.json \\ "validationStatus").head.as[String] mustBe "failure"
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

    def sendCreateValidationResourceRequest(body: String): Future[WSResponse] =
      wsUrl("/personal-details-validation")
        .addHttpHeaders(CONTENT_TYPE -> JSON)
        .post(body)
  }

}
