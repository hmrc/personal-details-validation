package uk.gov.hmrc.personaldetailsvalidation

import play.api.http.Status._
import play.mvc.Http.HeaderNames.LOCATION
import uk.gov.hmrc.support.BaseIntegrationSpec

class PersonalDetailsValidationISpec extends BaseIntegrationSpec {

  "POST /personal-details-validations" should {
    "should create a personal-details-validation resource" in new Setup {
      val request =
        """
          |{
          |   "firstName":"Joe",
          |   "lastName":"Ferguson",
          |   "nino":"AA000003D",
          |   "dateOfBirth":"2017-12-31"
          |}
        """.stripMargin
      val createResponse = createValidationResourceResponse(request).futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)

      val getResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK
      (getResponse.json \ "validationStatus").as[String] mustBe "success"
    }

    "return BAD Request if mandatory fields are missing" in new Setup {
      val createResponse = createValidationResourceResponse("{}").futureValue
      createResponse.status mustBe BAD_REQUEST
      val errors = (createResponse.json \ "errors").as[List[String]]
      errors must contain ("firstName is missing")
      errors must contain ("lastName is missing")
      errors must contain ("dateOfBirth is missing")
      errors must contain ("nino is missing")
    }
  }

  trait Setup {
    def createValidationResourceResponse(request: String) = wsUrl("/personal-details-validation").withHeaders("Content-Type" -> "application/json").post(request)
  }

}
