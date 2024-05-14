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

import com.github.tomakehurst.wiremock.client._
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import play.api.http.Status.OK

object PlatformAnalyticsStub extends Eventually with IntegrationPatience {

  private val url = urlEqualTo("/platform-analytics/event")

  def stubGAMatchEvent(): StubMapping = stubFor(post(url).willReturn(aResponse().withStatus(OK)))

  def verifyGAMatchEvent(label: String): Unit = {
    eventually {
      verify(postRequestedFor(url)
        .withRequestBody(matchingJsonPath("$.events[0].category", equalTo("sos_iv")))
        .withRequestBody(matchingJsonPath("$.events[0].action", equalTo("personal_detail_validation_result")))
        .withRequestBody(matchingJsonPath("$.events[0].label", equalTo(label)))
      )
    }
  }

  def verifyGAMatchEventNotCreated(label: String): Unit = {
    eventually {
      verify(new CountMatchingStrategy(CountMatchingStrategy.EQUAL_TO, 0), postRequestedFor(url)
        .withRequestBody(matchingJsonPath("$.events[0].category", equalTo("sos_iv")))
        .withRequestBody(matchingJsonPath("$.events[0].action", equalTo("personal_detail_validation_result")))
        .withRequestBody(matchingJsonPath("$.events[0].label", equalTo(label)))
      )
    }
  }
}
