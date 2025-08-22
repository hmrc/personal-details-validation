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
import org.scalatest.LoneElement
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Logger
import play.api.http.Status.OK
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.{CitizenDetailsConnector, CitizenDetailsConnectorConfig}
import uk.gov.hmrc.personaldetailsvalidation.model.Gender
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.support.utils.BaseIntegrationSpec
import uk.gov.hmrc.support.wiremock.WiremockStubs

import java.net.{URI, URL}

class CitizenDetailsConnectorISpec extends BaseIntegrationSpec
  with WiremockStubs
  with LogCapturing
  with LoneElement {

  private def testDesignatoryDetails(): JsObject =
    Json.parse("""{"person":{"sex":"F"}}""").as[JsObject]

  private def testInvalidDesignatoryDetails(): JsObject =
    Json.parse("""{ "person" : { "sex" : 11 }}""").as[JsObject]

  val nino: Nino = Nino("AA000003D")
  private def maskNino(nino: Nino): String = s"${nino.value.take(1)}XXXXX${nino.value.takeRight(3)}"

  val connectorConfig: CitizenDetailsConnectorConfig = new CitizenDetailsConnectorConfig(mock[HostConfigProvider]) {

    override lazy val baseUrl: URL = URI.create(s"http://localhost$port/citizen-details").toURL

  }

  lazy val connector: CitizenDetailsConnector = app.injector.instanceOf[CitizenDetailsConnector]

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
      val expectedUrl = s"/citizen-details/$nino/designatory-details"
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

  }
}
