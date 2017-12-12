/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import play.api.Configuration
import play.api.libs.json.{JsObject, Writes}
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.Future

trait HttpClientStubSetup extends MockFactory {
  private val configuration = mock[Configuration]
  (configuration.getString(_: String, _: Option[Set[String]]))
    .expects("appName", None)
    .returning(Some("personal-details-validation"))

  protected def expectPost(toUrl: String) = new {
    def withPayload(payload: JsObject) = new {

      def returning(status: Int): Unit =
        returning(HttpResponse(status))

      def returning(status: Int, body: String): Unit =
        returning(HttpResponse(status, responseString = Some(body)))

      def returning(response: HttpResponse): Unit =
        httpClient.postStubbing = (actualUrl: String, actualPayload: JsObject) => {
          actualUrl shouldBe toUrl
          actualPayload shouldBe payload
          response
        }
    }
  }

  class HttpClientStub
    extends HttpClient
      with WSHttp {

    override val hooks: Seq[HttpHook] = Nil

    private[HttpClientStubSetup] var postStubbing: (String, JsObject) => HttpResponse =
      (_, _) => throw new IllegalStateException("HttpClientStub not configured")

    override def doPost[A](url: String, body: A, headers: Seq[(String, String)])
                          (implicit wts: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = Future.successful {
      postStubbing(url, body.asInstanceOf[JsObject])
    }
  }

  val httpClient: HttpClientStub = new HttpClientStub()
}
