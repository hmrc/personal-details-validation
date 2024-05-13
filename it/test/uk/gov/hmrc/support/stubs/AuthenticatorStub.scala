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

package test.uk.gov.hmrc.support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status._
import play.api.libs.json.JsValue

object AuthenticatorStub {

  private val path: String = "/authenticator/match"

  def expecting(personalDetailsJson: String) = new {

    private val mappingBuilder = post(urlEqualTo(path)).withRequestBody(equalToJson(personalDetailsJson, true, false))

    def respondWithOK(): Unit = {
      stubFor(
        mappingBuilder.willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              """{
                | "firstName":"Jim",
                | "lastName":"Ferguson",
                | "dateOfBirth":"1948-04-23",
                | "nino":"AA000003D",
                | "saUtr":"1097133333"
                |}
                | """.stripMargin)
        )
      )
    }

    def respondWith(status: Int, body: Option[JsValue] = None): Unit = {
      stubFor(
        mappingBuilder.willReturn(
          aResponse().withStatus(status).withBody(body.map(_.toString()).getOrElse(""))
        )
      )
    }
  }

}
