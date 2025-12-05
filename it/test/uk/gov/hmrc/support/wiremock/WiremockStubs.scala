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

package uk.gov.hmrc.support.wiremock

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Writes

trait WiremockStubs {

  def stubPostWithRequestAndResponseBody[T](url: String,
                                            requestBody: T,
                                            expectedResponse: String,
                                            expectedStatus: Int,
                                            requestHeaders: Seq[HttpHeader] = Seq.empty)(implicit writes: Writes[T]): StubMapping = {
    val stringBody = writes.writes(requestBody).toString()

    val mapping: MappingBuilder = requestHeaders
      .foldLeft(post(urlMatching(url))) { (result, nxt) =>
        result.withHeader(nxt.key(), equalTo(nxt.firstValue()))
      }
      .withRequestBody(equalTo(stringBody))

    stubFor(
      mapping
        .willReturn(
          aResponse()
            .withStatus(expectedStatus)
            .withBody(expectedResponse)
            .withHeader("Content-Type", "application/json; charset=utf-8")))
  }

  def stubGetWithResponseBody(url: String,
                              expectedStatus: Int,
                              expectedResponse: String,
                              requestHeaders: Seq[HttpHeader] = Seq.empty): StubMapping = {
    val mappingWithHeaders: MappingBuilder = requestHeaders.foldLeft(get(urlMatching(url))) { (result, nxt) =>
      result.withHeader(nxt.key(), equalTo(nxt.firstValue()))
    }
    stubFor(
      mappingWithHeaders
        .willReturn(
          aResponse()
            .withStatus(expectedStatus)
            .withBody(expectedResponse)
            .withHeader("Content-Type", "application/json; charset=utf-8")))
  }

}
