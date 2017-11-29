package uk.gov.hmrc.personaldetailsvalidation

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status._
import play.api.test.FakeRequest
import play.mvc.Http.HeaderNames.LOCATION
import uk.gov.hmrc.play.test.UnitSpec

class PersonalDetailsValidationResourceControllerSpec extends UnitSpec with ScalaFutures {

  "Create in PersonalDetailsValidationResourceController" should {
    "return CREATED http status code" in new Setup {
      val response = controller.create()(request)
      status(response) shouldBe CREATED
    }

    "return uri of the new resource in response Location header" in new Setup {
      val response = controller.create()(request).futureValue
      response.header.headers(LOCATION) shouldBe routes.PersonalDetailsValidationResourceController.get().url
    }
  }

  "Get in PersonalDetailsValidationResourceController" should {
    "return OK http status code" in new Setup {
      val response = controller.get()(request).futureValue
      status(response) shouldBe OK
    }
    "return validationStatus as Success" in new Setup {
      val response = controller.get()(request).futureValue
      (jsonBodyOf(response) \ "validationStatus").as[String] shouldBe "success"
    }
  }

  trait Setup  {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val request = FakeRequest()
    val controller = new PersonalDetailsValidationResourceController
  }

}
