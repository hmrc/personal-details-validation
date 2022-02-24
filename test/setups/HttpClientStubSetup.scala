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

package setups

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import play.api.libs.json.{JsObject, Writes}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.{HttpClient, HttpResponse}
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.integration.servicemanager.AhcWsClientFactory
import uk.gov.hmrc.play.http.ws.WSHttp

import java.util.Collections
import scala.concurrent.{ExecutionContext, Future}

trait HttpClientStubSetup extends MockFactory {

  protected def expectPost(toUrl: String) = new {
    def withPayload(payload: JsObject) = new {

      def returning(status: Int): Unit =
        returning(HttpResponse(status, ""))

      def returning(status: Int, body: String): Unit =
        returning(HttpResponse(status, body))

      def returning(status: Int, body: JsObject): Unit =
        returning(HttpResponse(status, json = body, Map.empty))

      def returning(response: HttpResponse): Unit =
        httpClient.postStubbing = (actualUrl: String, actualPayload: JsObject) => {
          actualUrl shouldBe toUrl
          actualPayload shouldBe payload
          Future.successful(response)
        }

      def throwing(exception: Exception): Unit =
        httpClient.postStubbing =
          (actualUrl: String, actualPayload: JsObject) => {
            actualUrl shouldBe toUrl
            actualPayload shouldBe payload
            Future.failed(exception)
          }
    }
  }

  class HttpClientStub
    extends HttpClient
      with WSHttp {

    private val config = mock[Config]
    (config.getStringList _).expects(*).returning(Collections.emptyList()).anyNumberOfTimes()
    (config.hasPath _).expects(*).returning(true).anyNumberOfTimes()
    (config.getString _).expects(*).returning("").anyNumberOfTimes()
    (config.getDurationList(_: String)).expects(*).returning(Collections.emptyList()).anyNumberOfTimes()
    (config.getBoolean _).expects(*).returning(false).anyNumberOfTimes()

    implicit val mat: ActorMaterializer = ActorMaterializer()(actorSystem)

    override val wsClient: WSClient = AhcWsClientFactory.createClient()

    override val hooks: Seq[HttpHook] = Nil

    private[HttpClientStubSetup] var postStubbing: (String, JsObject) => Future[HttpResponse] =
      (_, _) => throw new IllegalStateException("HttpClientStub not configured")

    private var invoked = false

    override def doPost[A](url: String, body: A, headers: Seq[(String, String)])
                          (implicit rds: Writes[A], ec: ExecutionContext): Future[HttpResponse] = {
      invoked = true
      postStubbing(url, body.asInstanceOf[JsObject])
    }

    def assertInvocation(): Unit = if (!invoked) fail("stub was not invoked")

    override protected def actorSystem: ActorSystem = ActorSystem()

    override protected def configuration: Config = config
  }

  val httpClient: HttpClientStub = new HttpClientStub()
}
