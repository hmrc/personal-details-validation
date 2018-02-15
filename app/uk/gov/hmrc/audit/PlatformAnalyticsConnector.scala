/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import akka.Done
import play.api.libs.json.{JsObject, Json}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.random.RandomIntProvider

import scala.concurrent.ExecutionContext

@Singleton
class PlatformAnalyticsConnector(httpClient: HttpClient, connectorConfig: PlatformAnalyticsConnectorConfig, randomIntProvider: RandomIntProvider, logger: LoggerLike) {

  @Inject() def this(httpClient: HttpClient, connectorConfig: PlatformAnalyticsConnectorConfig, randomIntProvider: RandomIntProvider) = this(httpClient, connectorConfig, randomIntProvider, Logger)

  def sendEvent(event: GAEvent)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    httpClient.POST[JsObject, HttpResponse](
      url = s"${connectorConfig.baseUrl}/platform-analytics/event",
      body = event.toJson(hc.gaUserId.getOrElse(randomGaUserId))
    ).map(_ => Done).recover {
      case ex: Exception => logger.error("Unexpected response from platform-analytics", ex); Done
    }

  private def randomGaUserId = s"GA1.1.${Math.abs(randomIntProvider())}.${Math.abs(randomIntProvider())}"

  private implicit class GAEventSerializer(gaEvent: GAEvent) {
    def toJson(gaUserId: String): JsObject = Json.obj(
      "gaClientId" -> s"$gaUserId",
      "events" -> Json.arr(Json.obj(
        "category" -> s"${gaEvent.category}",
        "action" -> s"${gaEvent.action}",
        "label" -> s"${gaEvent.label}"
      ))
    )
  }

}
