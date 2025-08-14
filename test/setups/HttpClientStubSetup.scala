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

package setups

import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import play.api.libs.json.{JsObject, JsValue}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.net.URL
import java.util.Collections
import scala.concurrent.{ExecutionContext, Future}

trait HttpClientStubSetup extends MockFactory {

//  protected def expectPost(toUrl: String) = new {
//    def withPayload(payload: JsObject) = new {
//
//      def returning(status: Int): Unit =
//        returning(HttpResponse(status, ""))
//
//      def returning(status: Int, body: String): Unit =
//        returning(HttpResponse(status, body))
//
//      def returning(status: Int, body: JsObject): Unit =
//        returning(HttpResponse(status, json = body, Map.empty))
//
//      def returning(response: HttpResponse): Unit =
//        httpClient.postStubbing = (actualUrl: String, actualPayload: JsObject) => {
//          actualUrl shouldBe toUrl
//          actualPayload shouldBe payload
//          Future.successful(response)
//        }
//
//      def throwing(exception: Exception): Unit =
//        httpClient.postStubbing =
//          (actualUrl: String, actualPayload: JsObject) => {
//            actualUrl shouldBe toUrl
//            actualPayload shouldBe payload
//            Future.failed(exception)
//          }
//    }
//  }
//
//  protected def expectGet(toUrl: String) = new {
//    def returning(status: Int, body: JsValue): Unit =
//      returning(HttpResponse(status, json = body, Map.empty))
//
//    def returning(status: Int): Unit =
//      returning(HttpResponse(status, ""))
//
//    def returning(status: Int, body: String): Unit =
//      returning(HttpResponse(status, body))
//
//    def returning(response: HttpResponse): Unit =
//      httpClient.getStubbing = (actualUrl: String) => Future.successful {
//        actualUrl shouldBe toUrl
//        response
//      }
//
//    def throwing(exception: Exception): Unit =
//      httpClient.getStubbing = (actualUrl: String) => Future.failed {
//        actualUrl shouldBe toUrl
//        exception
//      }
//  }

//   abstract class HttpClientStub extends HttpClientV2 {
//
//    def mkRequestBuilder(url: URL, method: String)(hc: HeaderCarrier): RequestBuilder
//
//    private val config = mock[Config]
//    (config.getStringList _).expects(*).returning(Collections.emptyList()).anyNumberOfTimes()
//    (config.hasPath _).expects(*).returning(true).anyNumberOfTimes()
//    (config.getString _).expects(*).returning("").anyNumberOfTimes()
//    (config.getDurationList(_: String)).expects(*).returning(Collections.emptyList()).anyNumberOfTimes()
//    (config.getBoolean _).expects(*).returning(false).anyNumberOfTimes()
//
//    private[HttpClientStubSetup] var postStubbing: (String, JsObject) => Future[HttpResponse] =
//      (_, _) => throw new IllegalStateException("HttpClientStub not configured")
//
//    private[HttpClientStubSetup] var getStubbing: (String) => Future[HttpResponse] =
//      (_) => throw new IllegalStateException("HttpClientStub not configured")
//
//    private var invoked = false
//
//    def post(url: String, body: JsValue, headers: Seq[(String, String)])
//                     (implicit ec: ExecutionContext): Future[HttpResponse] = {
//      invoked = true
//      postStubbing(url, body.as[JsObject])
//    }
//
//    def get(url: String, headers: Seq[(String, String)])
//                    (implicit ec: ExecutionContext): Future[HttpResponse] = {
//      getStubbing(url)
//    }
//
//////    private[HttpClientStubSetup] var postStubbing: (String, JsObject) => Future[HttpResponse] =
//////      (_, _) => throw new IllegalStateException("HttpClientStub not configured")
//////
//////    private[HttpClientStubSetup] var getStubbing: (String) => Future[HttpResponse] =
//////      (_) => throw new IllegalStateException("HttpClientStub not configured")
////
////    private var invoked = false
////
//////    def post(url: String, body: JsValue, headers: Seq[(String, String)])
//////            (implicit ec: ExecutionContext): Future[HttpResponse] = {
//////      invoked = true
//////      postStubbing(url, body.as[JsObject])
//////    }
//////
//////    def get(url: String, headers: Seq[(String, String)])
//////           (implicit ec: ExecutionContext): Future[HttpResponse] = {
//////      getStubbing(url)
//////    }
//
//    def assertInvocation(): Unit = if (!invoked) fail("stub was not invoked")
//
//    implicit val actorSystem: ActorSystem = ActorSystem()
//    implicit val materializer: Materializer = Materializer.matFromSystem(actorSystem)
//
//
//  }
//
//  val httpClient: HttpClientStub = new HttpClientStub() {
//    override def mkRequestBuilder(url: URL, method: String)(hc: HeaderCarrier): RequestBuilder = ???
//  }
}
