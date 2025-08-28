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

package uk.gov.hmrc.personaldetailsvalidation.connectors

import ch.qos.logback.classic.Level
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, stubFor, urlEqualTo}
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.LoneElement
import play.api.http.Status.{INTERNAL_SERVER_ERROR, LOCKED, NOT_FOUND, OK}
import play.api.libs.json.{JsObject, Json}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.config.AppConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector
import uk.gov.hmrc.personaldetailsvalidation.model.Gender
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.support.utils.BaseIntegrationSpec
import uk.gov.hmrc.support.wiremock.WiremockStubs

class CitizenDetailsConnectorISpec extends BaseIntegrationSpec
  with WiremockStubs
  with LogCapturing
  with LoneElement {

  private def testDesignatoryDetails(): JsObject =
    Json.parse("""{"person":{"sex":"F"}}""").as[JsObject]

  private def testInvalidDesignatoryDetails(): JsObject =
    Json.parse("""{ "person" : { "sex" : 11 }}""").as[JsObject]

  private def errors(nino: String): JsObject =
    Json.parse(s"""{ "errors" : [ "Some failure for nino : $nino" ] }""").as[JsObject]

  val nino: Nino = Nino("AA000003D")
  val testBaseUrl = s"http://localhost:11111/citizen-details"

  private def maskNino(nino: Nino): String = s"${nino.value.take(1)}XXXXX${nino.value.takeRight(3)}"

  val connector: CitizenDetailsConnector = new CitizenDetailsConnector(
    httpClientV2,
    citizensDetailsConnectorConfig,
    appConfig
  )

  override def beforeEach(): Unit = {
    app.injector.instanceOf[AppConfig].cidDesignatoryDetailsCallEnabled
    super.beforeEach()
  }

  "Get designatory details" should {

    "details are returned with gender" in {

      stubGetWithResponseBody(
        url = s"/citizen-details/$nino/designatory-details",
        expectedStatus = OK,
        expectedResponse = testDesignatoryDetails().toString(),
      )

      connector.findDesignatoryDetails(nino).futureValue mustBe Some(Gender("F"))
    }

    "handle a response with an invalid body" in {
      val expectedUrl = s"http://localhost:11111/citizen-details/${maskNino(nino)}/designatory-details"

      stubGetWithResponseBody(
        url = s"/citizen-details/$nino/designatory-details",
        expectedStatus = OK,
        expectedResponse = testInvalidDesignatoryDetails().toString()
      )

      val expectedLog = s"Call to GET $expectedUrl returned invalid value for gender"

      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")) { logEvents =>

        val result = connector.findDesignatoryDetails(nino).futureValue
        result mustBe None

        eventually {
          val actualLog = logEvents.filter(_.getLevel == Level.ERROR).loneElement.getMessage
          actualLog mustBe expectedLog
        }
      }
    }

    "handle a response status of not found" in {
      stubGetWithResponseBody(
        url = s"/citizen-details/$nino/designatory-details",
        expectedStatus = NOT_FOUND,
        expectedResponse = testInvalidDesignatoryDetails().toString()
      )
      val expectedLog: String = s"Call to GET $testBaseUrl/${maskNino(nino)}/designatory-details returned not found"

      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")) { logEvents =>

        connector.findDesignatoryDetails(nino).futureValue mustBe None

        eventually {

          val actualLog: String = logEvents.filter(_.getLevel == Level.WARN).loneElement.getMessage

          actualLog mustBe expectedLog
        }
      }
    }

    "handle a response status of locked" in {
      stubGetWithResponseBody(
        url = s"/citizen-details/$nino/designatory-details",
        expectedStatus = LOCKED,
        expectedResponse = testInvalidDesignatoryDetails().toString()
      )

      val expectedLog: String = s"Call to GET $testBaseUrl/${maskNino(nino)}/designatory-details returned locked"

      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")) { logEvents =>

        connector.findDesignatoryDetails(nino).futureValue mustBe None

        eventually {

          val actualLog: String = logEvents.filter(_.getLevel == Level.WARN).loneElement.getMessage

          actualLog mustBe expectedLog
        }
      }
    }

    "handle a response status of internal server error" in {
      stubGetWithResponseBody(
        url = s"/citizen-details/$nino/designatory-details",
        expectedStatus = INTERNAL_SERVER_ERROR,
        expectedResponse = errors(nino.value).toString()
      )

      val expectedLog: String = s"""Call to GET $testBaseUrl/${maskNino(nino)}/designatory-details returned status 500 and body {  "errors" : [ "Some failure for nino : ${maskNino(nino)}" ]}"""

      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")) { logEvents =>

        connector.findDesignatoryDetails(nino).futureValue

        eventually {

          val actualLog: String = logEvents.filter(_.getLevel == Level.ERROR).loneElement.getMessage

          actualLog.replace("\n", "").replace(" ", "") mustBe expectedLog.replace(" ", "")
        }
      }
    }

    "not call CID if cidDesignatoryDetailsCallEnabled is set to false" in {

      val configData: Map[String, Boolean] = Map(
        "feature.nps-migration.cid-designatory-details-call.enabled" -> false
      )
      val config = Configuration.from(configData)

      val testAppConfig = new AppConfig(config) {
        val testLogger: Logger = logger
      }

      val connector: CitizenDetailsConnector = new CitizenDetailsConnector(
        null,
        null,
        testAppConfig
      )

      withCaptureOfLoggingFrom(testAppConfig.testLogger) { logEvents =>
        connector.findDesignatoryDetails(nino).futureValue mustBe None

        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage mustBe "[VER-3530] Designatory details call is DISABLED for NPS Migration"
        }
      }
    }

    "mask the user's nino when an exception is raised" in {

      stubFor(
        WireMock.get(urlEqualTo(s"/citizen-details/$nino/designatory-details"))
          .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)
          ))

      val maskedNino: String = s"${nino.value.take(1)}XXXXX${nino.value.takeRight(3)}"

      val expectedLog: String =
        s"Call to GET $testBaseUrl/$maskedNino/designatory-details threw: java.net.SocketException: Connection reset"

      withCaptureOfLoggingFrom(Logger("uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector")) { logEvents =>

        connector.findDesignatoryDetails(nino).futureValue

        eventually {

          val actualLog: String = logEvents.filter(_.getLevel == Level.ERROR).loneElement.getMessage

          actualLog mustBe expectedLog
        }

      }
    }

  }
}
