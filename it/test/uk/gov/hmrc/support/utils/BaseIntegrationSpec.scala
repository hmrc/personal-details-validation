/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.support.utils

import org.bson.BsonDocument
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import test.uk.gov.hmrc.support.wiremock.WiremockedServiceSupport
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.personaldetailsvalidation.{AssociationMongoRepository, PersonalDetailsValidationRepository}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.support.wiremock.WiremockSpecSupport

import scala.concurrent.ExecutionContext

trait BaseIntegrationSpec
  extends PlaySpec
    with GuiceOneServerPerSuite
    with ScalaFutures
    with IntegrationPatience
    with WiremockedServiceSupport
    with WiremockSpecSupport
    with Eventually
    with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier    = HeaderCarrier(sessionId = Some(SessionId("sessionIdValue")))

  override val wiremockedServices: List[String] = List("authenticator", "platform-analytics", "citizen-details", "auth")

  protected def additionalConfiguration = Map.empty[String, Any]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(additionalConfiguration ++ wiremockedServicesConfiguration + ("auditing.enabled" -> true)).build()

  protected implicit lazy val wsClient: WSClient =
    app.injector.instanceOf[WSClient]

  override def beforeEach(): Unit = {
    app.injector.instanceOf[PersonalDetailsValidationRepository].collection.deleteMany(new BsonDocument()).toFuture().futureValue
    app.injector.instanceOf[AssociationMongoRepository].collection.deleteMany(new BsonDocument()).toFuture().futureValue
    super.beforeEach()
  }

  lazy val httpClientV2: HttpClientV2         = app.injector.instanceOf[HttpClientV2]

  val mockAuditConnector: AuditConnector      = mock[AuditConnector]
  val mockHttpClient: HttpClientV2            = mock[HttpClientV2]
}
