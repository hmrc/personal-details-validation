/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.support.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlMatching}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.{JsNull, JsObject, Json}

object AuthStub {

  val credId: String = "cred-123"

  def authBody(credId: String): JsObject = Json.obj(
    "optionalCredentials" -> Json.obj(
      "providerId" -> credId,
      "providerType" -> "test2"
    )
  )

  val authBodyNoCredID: JsObject = Json.obj(
    "optionalCredentials" -> Json.obj(
      "providerId" -> JsNull,
      "providerType" -> "test2"
    )
  )


  def stubForAuth(statusCode: Int, body: JsObject = authBody(credId)): StubMapping = {
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withBody(Json.stringify(body))
            .withHeader("Content-type", "Application/json")
        )
    )
  }
}


