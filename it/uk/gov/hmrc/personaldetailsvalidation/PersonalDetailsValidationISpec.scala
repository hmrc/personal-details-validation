package uk.gov.hmrc.personaldetailsvalidation

import play.api.http.Status._
import uk.gov.hmrc.support.BaseIntegrationSpec

class PersonalDetailsValidationISpec extends BaseIntegrationSpec {

  "POST /personal-details-validations" should {
    "should create a resource" in {
      val response = wsUrl("/personal-details-validation").post("").futureValue
      response.status mustBe CREATED
    }
  }

}
