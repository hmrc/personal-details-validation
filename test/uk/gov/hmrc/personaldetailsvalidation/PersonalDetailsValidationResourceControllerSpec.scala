/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.personaldetailsvalidation

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.joda.time.LocalDate
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status._
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsNull, Json}
import play.api.test.FakeRequest
import play.mvc.Http.HeaderNames.LOCATION
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class PersonalDetailsValidationResourceControllerSpec extends UnitSpec with ScalaFutures with MockFactory {

  "Create in PersonalDetailsValidationResourceController" should {
    "return CREATED http status code" in new Setup {
      (mockRepository.create _).expects(personalDetails).returning(Future.successful(PersonalDetailsValidation(personalDetails)))

      val response = controller.create()(request.withBody(toJson(personalDetails)))

      status(response) shouldBe CREATED
    }

    "return uri of the new resource in response Location header" in new Setup {
      val personalDetailsValidation = PersonalDetailsValidation(personalDetails)
      (mockRepository.create _).expects(personalDetails).returning(Future.successful(personalDetailsValidation))

      val response = controller.create()(request.withBody(toJson(personalDetails))).futureValue

      response.header.headers(LOCATION) shouldBe routes.PersonalDetailsValidationResourceController.get(personalDetailsValidation.id).url
    }

    "return bad request if mandatory fields are missing" in new Setup {
      val response = controller.create()(request.withBody(JsNull))

      status(response) shouldBe BAD_REQUEST
    }

    "return errors if mandatory fields are missing" in new Setup {
      val response = controller.create()(request.withBody(JsNull)).futureValue

      val errors = (jsonBodyOf(response) \ "errors").as[List[String]]
      errors should contain ("firstName is missing")
      errors should contain ("lastName is missing")
      errors should contain ("dateOfBirth is missing")
      errors should contain ("nino is missing")
    }
  }

  "Get in PersonalDetailsValidationResourceController" should {
    "return OK http status code" in new Setup {
      val response = controller.get(PersonalDetailsValidationId())(request).futureValue
      status(response) shouldBe OK
    }
    "return validationStatus as Success" in new Setup {
      val response = controller.get(PersonalDetailsValidationId())(request).futureValue
      (jsonBodyOf(response) \ "validationStatus").as[String] shouldBe "success"
    }
  }

  trait Setup  {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val personalDetailsWrites = Json.writes[PersonalDetails]

    val personalDetails = PersonalDetails("some first name", "some last name", LocalDate.now(), Nino("AA000003D"))
    val request = FakeRequest()
    val mockRepository = mock[PersonalDetailsValidationRepository]
    val controller = new PersonalDetailsValidationResourceController(mockRepository)
  }

}
