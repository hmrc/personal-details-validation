package uk.gov.hmrc.personaldetailsvalidation

import play.api.http.Status._
import play.mvc.Http.HeaderNames.LOCATION
import uk.gov.hmrc.support.BaseIntegrationSpec

class PersonalDetailsValidationISpec extends BaseIntegrationSpec {

  "POST /personal-details-validations" should {
    "should create a personal-details-validation resource" in {
      val createResponse = wsUrl("/personal-details-validation").post("").futureValue
      createResponse.status mustBe CREATED
      val Some(resourceUrl) = createResponse.header(LOCATION)

      val getResponse = wsUrl(resourceUrl).get().futureValue
      getResponse.status mustBe OK
      (getResponse.json \ "validationStatus").as[String] mustBe "success"
    }
  }

}
