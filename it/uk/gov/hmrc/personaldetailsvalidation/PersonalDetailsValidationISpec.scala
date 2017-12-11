package uk.gov.hmrc.personaldetailsvalidation

import java.util.UUID.randomUUID

import play.api.http.ContentTypes.JSON
import play.api.http.Status._
import play.api.libs.json.{JsUndefined, JsValue, Json}
import play.mvc.Http.HeaderNames.{CONTENT_TYPE, LOCATION}
import uk.gov.hmrc.support.BaseIntegrationSpec

class PersonalDetailsValidationISpec extends BaseIntegrationSpec {

  "POST /personal-details-validations" should {

    "successfully validate when provided personal details can be matched by MDTP services" in new Setup {
      val personalDetails =
        """
          |{
          |   "firstName": "Jim",
          |   "lastName": "Ferguson",
          |   "nino": "AA000003D",
          |   "dateOfBirth": "1948-04-23"
          |}
        """.stripMargin
      val createResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)

      val getResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK

      val validationId = resourceUrl.substring(resourceUrl.lastIndexOf("/"))
      (getResponse.json \ "id").as[String] mustBe validationId
      (getResponse.json \ "validationStatus").as[String] mustBe "success"
      (getResponse.json \ "personalDetails").as[JsValue] mustBe Json.parse(personalDetails)
    }

    "return failure when provided personal details cannot be matched by MDTP services" in new Setup {
      val personalDetails =
        """
          |{
          |   "firstName": "John",
          |   "lastName": "Kowalski",
          |   "nino": "AA999999D",
          |   "dateOfBirth": "1948-04-23"
          |}
        """.stripMargin
      val createResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)

      val getResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK

      val validationId = resourceUrl.substring(resourceUrl.lastIndexOf("/"))
      (getResponse.json \ "id").as[String] mustBe validationId
      (getResponse.json \ "validationStatus").as[String] mustBe "failure"
      (getResponse.json \ "personalDetails") mustBe a[JsUndefined]
    }

    "return BAD Request if mandatory fields are missing" in new Setup {
      val createResponse = sendCreateValidationResourceRequest("{}").futureValue

      createResponse.status mustBe BAD_REQUEST

      (createResponse.json \ "errors").as[List[String]] must contain only(
        "firstName is missing",
        "lastName is missing",
        "dateOfBirth is missing",
        "nino is missing"
      )
    }
  }

  "GET /personal-details-validations/id" should {

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
    def sendCreateValidationResourceRequest(body: String) =
      wsUrl("/personal-details-validation")
        .withHeaders(CONTENT_TYPE -> JSON)
        .post(body)
  }
}
