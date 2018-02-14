package uk.gov.hmrc.support

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import uk.gov.hmrc.support.wiremock.{WiremockSpecSupport, WiremockedServiceSupport}

trait BaseIntegrationSpec
  extends PlaySpec
    with GuiceOneServerPerSuite
    with ScalaFutures
    with IntegrationPatience
    with WiremockedServiceSupport
    with WiremockSpecSupport
    with Eventually {

  override val wiremockedServices: List[String] = List("authenticator", "platform-analytics")

  protected def additionalConfiguration = Map.empty[String, Any]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(additionalConfiguration ++ wiremockedServicesConfiguration + ("auditing.enabled" -> true)).build()

  protected implicit lazy val wsClient: WSClient =
    app.injector.instanceOf[WSClient]
}
