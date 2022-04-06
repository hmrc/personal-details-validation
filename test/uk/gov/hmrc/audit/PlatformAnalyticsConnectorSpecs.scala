/*
 * Copyright 2022 HM Revenue & Customs
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
import org.scalamock.scalatest.MockFactory
import org.scalamock.scalatest.proxy.AsyncMockFactory
import play.api.LoggerLike
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.test.Helpers._
import setups.HttpClientStubSetup
import support.UnitSpec
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetailsWithNinoAndGender
import uk.gov.hmrc.random.RandomIntProvider

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Random

class PlatformAnalyticsConnectorSpecs extends UnitSpec with MockFactory {

  object Proxy extends AsyncMockFactory {
    import org.scalamock.proxy._
    def mock[T: ClassTag]: T with Mock = super.mock[T]
    def stub[T: ClassTag]: T with Stub = super.stub[T]
  }

  implicit val executionContext: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  "connector" should {
    "send event to platform analytics using gaUserId from header carrier" in new Setup {

      val gaUserId: String = "ga-user-id"

      (mockHttpClient.POST[JsObject, HttpResponse](_: String, _: JsObject, _: Seq[(String, String)])(_ : Writes[JsObject], _ : HttpReads[HttpResponse], _ : HeaderCarrier, _ : ExecutionContext))
        .expects(*, payload(gaUserId), *, *, *, *, *).returning(Future.successful(HttpResponse(200, "")))

      expectPost(toUrl = s"${connectorConfig.baseUrl}/platform-analytics/event")
        .withPayload(payload(gaUserId))
        .returning(OK)

      implicit val hc: HeaderCarrier = headerCarrier.copy(gaUserId = Option(gaUserId))

      connector.sendEvent(gaEvent, origin, None)

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

      connector.sendEvent(gaEvent, origin, None)

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

      connector.sendEvent(gaEvent, origin, None)(headerCarrier.copy(gaUserId = Option(gaUserId)), executionContext)

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

    "send event to platform analytics using gender and age" in new Setup {

      override def payload(gaUserId: String) = Json.obj(
        "gaClientId" -> s"$gaUserId",
        "events" -> Json.arr(Json.obj(
          "category" -> s"${gaEvent.category}",
          "action" -> s"${gaEvent.action}",
          "label" -> s"${gaEvent.label}",
          "dimensions" -> Json.arr(
            Json.obj("index" -> 2, "value" -> "Test"),
            Json.obj("index" -> 3, "value" -> "20"),
            Json.obj("index" -> 1, "value" -> "genderF")
          )
        ))
      )

      val randomValue1 = Random.nextInt()
      val randomValue2 = Random.nextInt()

      randomIntProvider.apply _ when() returns randomValue1 noMoreThanOnce()
      randomIntProvider.apply _ when() returns randomValue2

      expectPost(toUrl = s"${connectorConfig.baseUrl}/platform-analytics/event")
        .withPayload(payload(s"GA1.1.${Math.abs(randomValue1)}.${Math.abs(randomValue2)}"))
        .returning(OK)

      implicit val hc = headerCarrier

      connector.sendEvent(gaEvent, origin, Some(PersonalDetailsWithNinoAndGender("firstName", "lastName", LocalDate.now().minusYears(20), Nino("AA000003D"), "genderF")))

      httpClient.assertInvocation()
    }

  }

  private trait Setup extends HttpClientStubSetup {
    val headerCarrier: HeaderCarrier = HeaderCarrier()

    val gaEvent = GAEvent("some-label", "some-action", "some-category")

    val testBaseUrl = "http://localhost:9000"

    val mockHostConfigProvider = mock[HostConfigProvider]

    def payload(gaUserId: String) = Json.obj(
      "gaClientId" -> s"$gaUserId",
      "events" -> Json.arr(Json.obj(
        "category" -> s"${gaEvent.category}",
        "action" -> s"${gaEvent.action}",
        "label" -> s"${gaEvent.label}",
        "dimensions" -> Json.arr(Json.obj("index" -> 2, "value" -> "Test"))
      ))
    )

    val connectorConfig = new PlatformAnalyticsConnectorConfig(mockHostConfigProvider) {
      override lazy val baseUrl = testBaseUrl
      override lazy val gaGenderDimension: Int = 1
      override lazy val gaOriginDimension: Int = 2
      override lazy val gaAgeDimension: Int = 3
    }

    val origin = Some("Test")
    val randomIntProvider = stub[RandomIntProvider]
    val logger: LoggerLike with Stub = Proxy.stub[LoggerLike]
    // TODO: logger is not used ... in-fact, this test does not even appear to be RAN??
    // TODO: Not sure why this entire class is here

    val connector = new PlatformAnalyticsConnector(httpClient, connectorConfig, randomIntProvider)
    val mockHttpClient: HttpClient = mock[HttpClient]
  }

}
