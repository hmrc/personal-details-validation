package uk.gov.hmrc.personaldetailsvalidation

import java.util.UUID.randomUUID

import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.mvc.Http.HeaderNames.LOCATION
import uk.gov.hmrc.support.BaseIntegrationSpec

class PersonalDetailsValidationISpec extends BaseIntegrationSpec {

  "POST /personal-details-validations" should {
    "create a personal-details-validation resource" in new Setup {
      val personalDetails =
        """
          |{
          |   "firstName":"Joe",
          |   "lastName":"Ferguson",
          |   "nino":"AA000003D",
          |   "dateOfBirth":"2017-12-31"
          |}
        """.stripMargin
      val createResponse = sendCreateValidationResourceRequest(personalDetails).futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)

      val getResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK
      resourceUrl must endWith ((getResponse.json \ "id").as[String])
      (getResponse.json \ "validationStatus").as[String] mustBe "success"
      (getResponse.json \ "personalDetails").as[JsValue] mustBe Json.parse(personalDetails)
    }

    "return BAD Request if mandatory fields are missing" in new Setup {
      val createResponse = sendCreateValidationResourceRequest("{}").futureValue
      createResponse.status mustBe BAD_REQUEST
      val errors = (createResponse.json \ "errors").as[List[String]]
      errors must contain ("firstName is missing")
      errors must contain ("lastName is missing")
      errors must contain ("dateOfBirth is missing")
      errors must contain ("nino is missing")
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

  trait Setup {
    def sendCreateValidationResourceRequest(body: String) = wsUrl("/personal-details-validation").withHeaders("Content-Type" -> "application/json").post(body)
  }

}
