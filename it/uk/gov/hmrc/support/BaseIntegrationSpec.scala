package uk.gov.hmrc.support

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient

trait BaseIntegrationSpec
  extends PlaySpec
    with GuiceOneServerPerSuite
    with ScalaFutures
    with IntegrationPatience {

  protected def additionalConfiguration = Map.empty[String, Any]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(additionalConfiguration).build()

  protected implicit lazy val wsClient: WSClient =
    app.injector.instanceOf[WSClient]
}
