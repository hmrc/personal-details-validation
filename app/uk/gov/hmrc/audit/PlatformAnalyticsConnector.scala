package uk.gov.hmrc.audit

import javax.inject.{Inject, Singleton}

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.random.RandomIntProvider

import scala.concurrent.ExecutionContext

@Singleton
class PlatformAnalyticsConnector @Inject()(httpClient: HttpClient, connectorConfig: PlatformAnalyticsConnectorConfig, randomIntProvider: RandomIntProvider) {

  def sendEvent(event: GAEvent)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    httpClient.POST[JsObject, HttpResponse](
      url = s"${connectorConfig.baseUrl}/event",
      body = event.toJson(hc.gaUserId.getOrElse(randomGaUserId))
    )

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
