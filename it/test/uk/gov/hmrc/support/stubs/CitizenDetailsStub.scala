/*
 * Copyright 2025 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import play.api.http.Status.OK
import play.api.libs.json.JsValue

object CitizenDetailsStub extends Eventually with IntegrationPatience {

  private val path: String = "/citizen-details/AA000003D/designatory-details"

  def expecting() = new {

    private val mappingBuilder = get(urlEqualTo(path))

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
