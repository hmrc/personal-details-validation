/*
 * Copyright 2019 HM Revenue & Customs
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

import org.scalamock.scalatest.MixedMockFactory
import org.scalatest.concurrent.Eventually
import play.api.LoggerLike
import play.api.libs.json.Json
import play.api.test.Helpers._
import setups.HttpClientStubSetup
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.random.RandomIntProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.concurrent.duration._

class PlatformAnalyticsConnectorSpecs extends UnitSpec with MixedMockFactory with Eventually {

  "connector" should {

    "send event to platform analytics using gaUserId from header carrier" in new Setup {
      val gaUserId = "ga-user-id"

      expectPost(toUrl = s"${connectorConfig.baseUrl}/platform-analytics/event")
        .withPayload(payload(gaUserId))
        .returning(OK)

      connector.sendEvent(gaEvent)(headerCarrier.copy(gaUserId = Some(gaUserId)), global)

      httpClient.assertInvocation
    }

    "send event to platform analytics using random gaUserId if gaUserId absent in header carrier" in new Setup {

      val randomValue1 = Random.nextInt()
      val randomValue2 = Random.nextInt()

      randomIntProvider.apply _ when() returns randomValue1 noMoreThanOnce()
      randomIntProvider.apply _ when() returns randomValue2

      expectPost(toUrl = s"${connectorConfig.baseUrl}/platform-analytics/event")
        .withPayload(payload(s"GA1.1.${Math.abs(randomValue1)}.${Math.abs(randomValue2)}"))
        .returning(OK)

      connector.sendEvent(gaEvent)

      httpClient.assertInvocation
    }

    "log error if call to platform-analytics fail" in new Setup {
      val gaUserId = "ga-user-id"

      val httpResponseStatus = BAD_GATEWAY
      val httpResponseBody = "some-error-response-body"

      expectPost(toUrl = s"${connectorConfig.baseUrl}/platform-analytics/event")
        .withPayload(payload(gaUserId))
        .returning(httpResponseStatus, httpResponseBody)

      logger.when('error)(*,*)

      connector.sendEvent(gaEvent)(headerCarrier.copy(gaUserId = Some(gaUserId)), global)

      implicit val patienceConfig = PatienceConfig(timeout = 5 seconds, interval = 100 millis)

      eventually {
        logger.verify('error)(
          argAssert { (message: () => String) =>
            message() shouldBe "Unexpected response from platform-analytics"
          },
          argAssert { (throwable: () => Throwable) =>
            throwable() shouldBe a[Upstream5xxResponse]
          })
      }
    }
  }

  private trait Setup extends HttpClientStubSetup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

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
    val logger = Proxy.stub[LoggerLike]

    val connector = new PlatformAnalyticsConnector(httpClient, connectorConfig, randomIntProvider, logger)
  }

}
