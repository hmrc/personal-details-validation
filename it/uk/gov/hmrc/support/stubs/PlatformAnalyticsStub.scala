package uk.gov.hmrc.support.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

object PlatformAnalyticsStub extends Eventually with IntegrationPatience {

  def verifyGAMatchEvent(label: String) = {
    eventually {
      verify(postRequestedFor(urlEqualTo("/platform-analytics/event"))
        .withRequestBody(matchingJsonPath("$.events[0].category", equalTo("sos_iv")))
        .withRequestBody(matchingJsonPath("$.events[0].action", equalTo("personal_detail_validation_result")))
        .withRequestBody(matchingJsonPath("$.events[0].label", equalTo(label)))
      )
    }
  }
}
