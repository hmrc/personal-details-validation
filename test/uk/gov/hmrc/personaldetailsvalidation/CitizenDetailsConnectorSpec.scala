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

package uk.gov.hmrc.personaldetailsvalidation

import ch.qos.logback.classic.Level
import generators.Generators.Implicits._
import generators.ObjectGenerators.personalDetailsWithNinoObjects
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{JsObject, Json}
import play.api.{Configuration, Logger}
import play.api.test.Helpers.{INTERNAL_SERVER_ERROR, LOCKED, NOT_FOUND, OK}
import support.UnitSpec
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import scala.concurrent.ExecutionContext.Implicits.global

class CitizenDetailsConnectorSpec
  extends UnitSpec with ScalaCheckDrivenPropertyChecks
    with LoneElement with Eventually with LogCapturing {

  "Get designatory details" should {


//        "handle a response with an invalid body" in new Setup {
//
//          expectGet(toUrl = s"$testBaseUrl/$nino/designatory-details")
//            .returning(OK, testInvalidDesignatoryDetails())
//
//          val expectedLog: String = s"Call to GET $testBaseUrl/${maskNino(nino)}/designatory-details returned invalid value for gender"
//
//          withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")){ logEvents =>
//
//            connector.findDesignatoryDetails(nino).futureValue shouldBe None
//
//            eventually {
//
//              val actualLog: String = logEvents.filter(_.getLevel == Level.ERROR).loneElement.getMessage
//
//              actualLog shouldBe expectedLog
//            }
//
//          }
//
//        }

    //    "handle a response status of not found" in new Setup {
    //
    //      expectGet(toUrl = s"$testBaseUrl/$nino/designatory-details")
    //        .returning(NOT_FOUND)
    //
    //      val expectedLog: String = s"Call to GET $testBaseUrl/${maskNino(nino)}/designatory-details returned not found"
    //
    //      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")) { logEvents =>
    //
    //        connector.findDesignatoryDetails(nino).futureValue shouldBe None
    //
    //        eventually {
    //
    //          val actualLog: String = logEvents.filter(_.getLevel == Level.WARN).loneElement.getMessage
    //
    //          actualLog shouldBe expectedLog
    //        }
    //      }
    //
    //    }
    //
    //    "handle a response status of locked" in new Setup {
    //
    //      expectGet(toUrl = s"$testBaseUrl/$nino/designatory-details")
    //        .returning(LOCKED)
    //
    //      val expectedLog: String = s"Call to GET $testBaseUrl/${maskNino(nino)}/designatory-details returned locked"
    //
    //      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")) { logEvents =>
    //
    //        connector.findDesignatoryDetails(nino).futureValue shouldBe None
    //
    //        eventually {
    //
    //          val actualLog: String = logEvents.filter(_.getLevel == Level.WARN).loneElement.getMessage
    //
    //          actualLog shouldBe expectedLog
    //        }
    //
    //      }
    //    }
    //
    //    "handle a response status of internal server error" in new Setup {
    //
    //      expectGet(toUrl = s"$testBaseUrl/$nino/designatory-details")
    //        .returning(INTERNAL_SERVER_ERROR, errors(nino.value))
    //
    //      val expectedLog: String = s"""Call to GET $testBaseUrl/${maskNino(nino)}/designatory-details returned status 500 and body {  "errors" : [ "Some failure for nino : ${maskNino(nino)}" ]}"""
    //
    //      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")) { logEvents =>
    //
    //        connector.findDesignatoryDetails(nino).futureValue
    //
    //        eventually {
    //
    //          val actualLog: String = logEvents.filter(_.getLevel == Level.ERROR).loneElement.getMessage
    //
    //          actualLog.replace("\n", "").replace(" ", "") shouldBe expectedLog.replace(" ", "")
    //        }
    //
    //      }
    //
    //    }
    //
    //    "not call CID if cidDesignatoryDetailsCallEnabled is set to false" in new Setup(false) {
    //      withCaptureOfLoggingFrom(appConfig.testLogger) { logEvents =>
    //        connector.findDesignatoryDetails(nino).futureValue shouldBe None
    //
    //        eventually {
    //          logEvents
    //            .filter(_.getLevel == Level.WARN)
    //            .loneElement
    //            .getMessage shouldBe "[VER-3530] Designatory details call is DISABLED for NPS Migration"
    //        }
    //      }
    //    }
    //
    //    "mask the user's nino when an exception is raised" in new Setup {
    //
    //      val url: String = s"$testBaseUrl/$nino/designatory-details"
    //
    //      val errMsg: String = s"GET of '$url' returned 423. Response body: ''"
    //
    //      val exception: UpstreamErrorResponse = UpstreamErrorResponse(errMsg, LOCKED)
    //
    //      expectGet(url).throwing(exception)
    //
    //      val maskedNino: String = s"${nino.value.take(1)}XXXXX${nino.value.takeRight(3)}"
    //
    //      val expectedLog: String = s"Call to GET $testBaseUrl/$maskedNino/designatory-details threw: uk.gov.hmrc.http.Upstream4xxResponse: GET of '$testBaseUrl/$maskedNino/designatory-details' returned 423. Response body: ''"
    //
    //      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")) { logEvents =>
    //
    //        connector.findDesignatoryDetails(nino)
    //
    //        eventually {
    //
    //          val actualLog: String =  logEvents.filter(_.getLevel == Level.ERROR).loneElement.getMessage
    //
    //          actualLog shouldBe expectedLog
    //        }
    //
    //      }
    //    }
  }

  private class Setup(isCidDesignatoryDetailsCallEnabled: Boolean = true) {
    //    val expectedHost = "localhost"
    //    val expectedPort = 9337
    //    lazy val additionalConfig: Map[String, Any] = Map.empty
    //    private lazy val configData: Map[String, Any] = Map(
    //      "feature.nps-migration.cid-designatory-details-call.enabled" -> isCidDesignatoryDetailsCallEnabled
    //    ) ++ additionalConfig
    //          val config = Configuration.from(configData)
    //          lazy val appConfig = new AppConfig(config) {
    //            val testLogger: Logger = logger
    //          }
    //
    //
    //          val hostConfigProvider: HostConfigProvider = mock[HostConfigProvider]
    //          lazy val connectorConfig: CitizenDetailsConnectorConfig = new CitizenDetailsConnectorConfig(hostConfigProvider) {
    //            override lazy val baseUrl: String = testBaseUrl
    //          }
    //
    //        implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    //
    //          val nino: Nino = personalDetailsWithNinoObjects.generateOne.nino
    //
    //          val testBaseUrl: String = s"http://$expectedHost:$expectedPort/citizen-details"
    //
    //          val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
    //
    //        val connector = new CitizenDetailsConnector(mockHttpClient, connectorConfig, appConfig)
    //      }
    //
    //      private def maskNino(nino: Nino): String = s"${nino.value.take(1)}XXXXX${nino.value.takeRight(3)}"
    //
    //      private def testDesignatoryDetails(): JsObject =
    //        Json.parse("""{"person":{"sex":"F"}}""").as[JsObject]
    //
    //      private def testInvalidDesignatoryDetails(): JsObject =
    //        Json.parse("""{ "person" : { "sex" : 11 }}""").as[JsObject]
    //
    //      private def errors(nino: String): JsObject =
    //        Json.parse(s"""{ "errors" : [ "Some failure for nino : $nino" ] }""").as[JsObject]
    //
    //  }
  }
}
