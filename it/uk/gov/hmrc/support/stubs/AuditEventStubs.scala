package uk.gov.hmrc.support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status.OK
import uk.gov.hmrc.support.stubs.PlatformAnalyticsStub.eventually

object AuditEventStubs {

  private val url = urlEqualTo("/write/audit")

  def stubAuditEvent() = stubFor(post(url).willReturn(aResponse().withStatus(OK)))

  def verifyAuditEvent(matchingStatus: String) = {
    eventually {
      verify(postRequestedFor(url)
        .withRequestBody(matchingJsonPath("$.auditSource", equalTo("personal-details-validation")))
        .withRequestBody(matchingJsonPath("$.auditType", equalTo("MatchingResult")))
        .withRequestBody(matchingJsonPath("$.detail.matchingStatus", equalTo(matchingStatus)))
      )
    }
  }
}
