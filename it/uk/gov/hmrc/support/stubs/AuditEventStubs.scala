package uk.gov.hmrc.support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.OK
import uk.gov.hmrc.support.stubs.PlatformAnalyticsStub.eventually

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
