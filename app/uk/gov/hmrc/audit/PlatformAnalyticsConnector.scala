/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Request
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetails
import uk.gov.hmrc.random.RandomIntProvider

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PlatformAnalyticsConnector @Inject()(httpClient: HttpClient, connectorConfig: PlatformAnalyticsConnectorConfig, randomIntProvider: RandomIntProvider)
  extends Logging {

  def sendEvent(event: GAEvent, loginOrigin: Option[String], maybePersonalDetails: Option[PersonalDetails] = None)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val origin = loginOrigin.getOrElse(hc.otherHeaders.toMap.getOrElse("origin", "Unknown-Origin"))

    implicit val dimensionWrites: OWrites[DimensionValue] = Json.writes[DimensionValue]
    implicit val eventWrites: OWrites[Event] = Json.writes[Event]
    implicit val analyticsWrites: OWrites[AnalyticsRequest] = Json.writes[AnalyticsRequest]

    val dimensions: Seq[DimensionValue] = Seq(
      Some(DimensionValue(connectorConfig.gaOriginDimension, origin)),
      maybePersonalDetails.map(personalDetails => DimensionValue(connectorConfig.gaAgeDimension, personalDetails.age.toString)),
      maybePersonalDetails.flatMap(personalDetails => personalDetails.maybeGender.map(gender => DimensionValue(connectorConfig.gaGenderDimension, gender.value)))
    ).flatten

    val newEvent = Event(event.category, event.action, event.label, dimensions)

    val gaClientId: String = getGaClientId()

    val analyticsRequest = AnalyticsRequest(gaClientId, Seq(newEvent))

    logger.info(s"Sending ga event $analyticsRequest")

    httpClient.POST[AnalyticsRequest, HttpResponse](
      url = s"${connectorConfig.baseUrl}/platform-analytics/event",
      body = analyticsRequest
    ).map(_ => Done).recover {
      case ex: Exception => logger.error("Unexpected response from platform-analytics", ex); Done
    }
  }

  def getGaClientId()(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): String = {
    val gaIdInHeaders: Option[String] = request.headers.get("_ga")
    lazy val randomGaUserId = s"GA1.1.${Math.abs(randomIntProvider())}.${Math.abs(randomIntProvider())}"
    if (gaIdInHeaders.isEmpty) {
      logger.info("Unable to get users' _gaId - No gaClientId found in request")
    }
    gaIdInHeaders.getOrElse(randomGaUserId)
  }

}
