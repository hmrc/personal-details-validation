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
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.Eventually.eventually
import play.api.http.Status.OK

object AuditEventStubs {

  private val url = urlEqualTo("/write/audit")

  def stubAuditEvent(): StubMapping = stubFor(post(url).willReturn(aResponse().withStatus(OK)))

  def verifyMatchingStatusInAuditEvent(matchingStatus: String): Unit = {
    eventually {
      verify(matchingResultAuditEventRequestBuilder
        .withRequestBody(matchingJsonPath("$.detail.matchingStatus", equalTo(matchingStatus)))
      )
    }
  }

  def verifyFailureDetailInAuditEvent(failureDetail: String): Unit = {
    eventually {
      verify(matchingResultAuditEventRequestBuilder
        .withRequestBody(matchingJsonPath("$.detail.failureDetail", equalTo(failureDetail)))
      )
    }
  }

  private def matchingResultAuditEventRequestBuilder: RequestPatternBuilder = postRequestedFor(url)
    .withRequestBody(matchingJsonPath("$.auditSource", equalTo("personal-details-validation")))
    .withRequestBody(matchingJsonPath("$.auditType", equalTo("MatchingResult")))
}
