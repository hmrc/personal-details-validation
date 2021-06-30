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

import akka.Done
import play.api.Logging
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.random.RandomIntProvider

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PlatformAnalyticsConnector @Inject()(httpClient: HttpClient, connectorConfig: PlatformAnalyticsConnectorConfig, randomIntProvider: RandomIntProvider) extends Logging {

  def sendEvent(event: GAEvent)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val origin = hc.otherHeaders.toMap.getOrElse("origin", "Unknown-Origin")
    httpClient.POST[JsObject, HttpResponse](
      url = s"${connectorConfig.baseUrl}/platform-analytics/event",
      body = event.toJson(hc.gaUserId.getOrElse(randomGaUserId), origin)
    ).map(_ => Done).recover {
      case ex: Exception => logger.error("Unexpected response from platform-analytics", ex); Done
    }
  }

  private def randomGaUserId = s"GA1.1.${Math.abs(randomIntProvider())}.${Math.abs(randomIntProvider())}"

  private implicit class GAEventSerializer(gaEvent: GAEvent) {
    def toJson(gaUserId: String, origin: String): JsObject = {
      Json.obj(
        "gaClientId" -> s"$gaUserId",
        "events" -> Json.arr(Json.obj(
          "category" -> s"${gaEvent.category}",
          "action" -> s"${gaEvent.action}",
          "label" -> s"${gaEvent.label}",
          "origin" -> s"$origin"
        ))
      )
    }
  }

}
