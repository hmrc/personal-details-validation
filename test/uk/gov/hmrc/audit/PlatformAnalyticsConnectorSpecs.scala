/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.audit

import org.scalamock.proxy.Stub
import org.scalamock.scalatest.proxy.AsyncMockFactory
import play.api.LoggerLike
import play.api.libs.json.Json
import play.api.test.Helpers._
import setups.HttpClientStubSetup
import support.UnitSpec
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.random.RandomIntProvider

import scala.reflect.ClassTag
import scala.util.Random

class PlatformAnalyticsConnectorSpecs extends UnitSpec with AsyncMockFactory {

  object Proxy extends AsyncMockFactory {
    import org.scalamock.proxy._
    def mock[T: ClassTag]: T with Mock = super.mock[T]
    def stub[T: ClassTag]: T with Stub = super.stub[T]
  }

  "connector" should {

    "send event to platform analytics using gaUserId from header carrier" in new Setup {

      val gaUserId: String = "ga-user-id"

      expectPost(toUrl = s"${connectorConfig.baseUrl}/platform-analytics/event")
        .withPayload(payload(gaUserId))
        .returning(OK)

      implicit val hc: HeaderCarrier = headerCarrier.copy(gaUserId = Option(gaUserId))

      connector.sendEvent(gaEvent)

      httpClient.assertInvocation()
    }

    "send event to platform analytics using random gaUserId if gaUserId absent in header carrier" in new Setup {

      val randomValue1 = Random.nextInt()
      val randomValue2 = Random.nextInt()

      randomIntProvider.apply _ when() returns randomValue1 noMoreThanOnce()
      randomIntProvider.apply _ when() returns randomValue2

      expectPost(toUrl = s"${connectorConfig.baseUrl}/platform-analytics/event")
        .withPayload(payload(s"GA1.1.${Math.abs(randomValue1)}.${Math.abs(randomValue2)}"))
        .returning(OK)

      implicit val hc = headerCarrier

      connector.sendEvent(gaEvent)

      httpClient.assertInvocation()
    }

    "log error if call to platform-analytics fail" in new Setup {

      val gaUserId = "ga-user-id"

      val httpResponseStatus = BAD_GATEWAY
      val httpResponseBody = "some-error-response-body"

      expectPost(toUrl = s"${connectorConfig.baseUrl}/platform-analytics/event")
        .withPayload(payload(gaUserId))
        .returning(httpResponseStatus, httpResponseBody)

      logger.when('error)(*,*,*)

      connector.sendEvent(gaEvent)(headerCarrier.copy(gaUserId = Option(gaUserId)), executionContext)

      logger.verify('error)(
        argAssert { (message: () => String) =>
          message() shouldBe "Unexpected response from platform-analytics"
        },
        argAssert { (throwable: () => Throwable) =>
          throwable() shouldBe a[UpstreamErrorResponse]
        },
        *
      )

    }
  }

  private trait Setup extends HttpClientStubSetup {

    val headerCarrier: HeaderCarrier = HeaderCarrier()

    val gaEvent = GAEvent("some-label", "some-action", "some-category")

    def payload(gaUserId: String) = Json.obj(
      "gaClientId" -> s"$gaUserId",
      "events" -> Json.arr(Json.obj(
        "category" -> s"${gaEvent.category}",
        "action" -> s"${gaEvent.action}",
        "label" -> s"${gaEvent.label}"
      ))
    )

    val testBaseUrl = "http://localhost:9000"
    val connectorConfig = new PlatformAnalyticsConnectorConfig(mock[HostConfigProvider]) {
      override lazy val baseUrl = testBaseUrl
    }

    val randomIntProvider = stub[RandomIntProvider]
    val logger: LoggerLike with Stub = Proxy.stub[LoggerLike]
    // TODO: logger is not used ... in-fact, this test does not even appear to be RAN??
    // TODO: Not sure why this entire class is here

    val connector = new PlatformAnalyticsConnector(httpClient, connectorConfig, randomIntProvider)
  }

}
