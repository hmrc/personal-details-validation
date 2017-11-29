package uk.gov.hmrc.personaldetailsvalidation

import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status._
import play.api.test.FakeRequest
import play.mvc.Http.HeaderNames.LOCATION
import uk.gov.hmrc.play.test.UnitSpec

class PersonalDetailsValidationResourceControllerSpec extends UnitSpec with ScalaFutures {

  "Create in PersonalDetailsValidationResourceController" should {
    "return CREATED http status code for valid request" in new Setup {
      val response = controller.create()(request)
      status(response) shouldBe CREATED
    }

    "return uri of the new resource in response Location header" in new Setup {
      val response = controller.create()(request).futureValue
      response.header.headers(LOCATION) shouldBe routes.PersonalDetailsValidationResourceController.get().url
    }
  }

  trait Setup {
    val request = FakeRequest()
    val controller = new PersonalDetailsValidationResourceController
  }

}
