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

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.JsString
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec

class HelloWorldEndpointSpec
  extends UnitSpec
    with ScalaFutures {

  private val request = FakeRequest("GET", "/")

  "helloWorld" should {
    "return OK with Json body" in {
      val result = new HelloWorldEndpoint().helloWorld()(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe JsString("Hello world")
    }
  }
}
