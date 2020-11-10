package uk.gov.hmrc.support.stubs

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
