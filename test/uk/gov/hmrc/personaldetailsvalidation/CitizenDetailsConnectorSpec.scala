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

package uk.gov.hmrc.personaldetailsvalidation

import ch.qos.logback.classic.Level
import generators.Generators.Implicits._
import generators.ObjectGenerators.personalDetailsWithNinoObjects
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{JsObject, Json}
import play.api.{Configuration, Logger}
import setups.HttpClientStubSetup
import support.UnitSpec
import uk.gov.hmrc.config.{AppConfig, HostConfigProvider}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import scala.concurrent.ExecutionContext.Implicits.global

class CitizenDetailsConnectorSpec
  extends UnitSpec with ScalaCheckDrivenPropertyChecks with HttpClientStubSetup
    with LoneElement with Eventually with LogCapturing {

  "Get designatory details" should {
    "details are returned with gender" in new Setup {
        expectGet(toUrl = s"$testBaseUrl/$nino/designatory-details")
          .returning(200, testDesignatoryDetails())

        connector.findDesignatoryDetails(nino).futureValue shouldBe Some(Gender("F"))
    }
    "not call CID if isCidDesignatoryDetailsCallEnabled is set to false" in new Setup(false) {
      withCaptureOfLoggingFrom(connector.testLogger) { logEvents =>
        connector.findDesignatoryDetails(nino).futureValue shouldBe None

        eventually {
          logEvents
            .filter(_.getLevel == Level.WARN)
            .loneElement
            .getMessage shouldBe "[VER-3530] Designatory details call is DISABLED for NPS Migration"
        }
      }
    }
  }

    private class Setup(isCidDesignatoryDetailsCallEnabled : Boolean = true) {
    val expectedHost = "localhost"
    val expectedPort = 9337
      lazy val additionalConfig: Map[String, Any] = Map.empty
      private lazy val configData: Map[String, Any] = Map(
        "feature.nps-migration.cid-designatory-details-call.enabled" -> isCidDesignatoryDetailsCallEnabled
      ) ++ additionalConfig
      val config = Configuration.from(configData)
      lazy val appConfig = new AppConfig(config)


    val hostConfigProvider = mock[HostConfigProvider]
    lazy val connectorConfig = new CitizenDetailsConnectorConfig(hostConfigProvider){
      override lazy val baseUrl = testBaseUrl
    }

    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val nino = personalDetailsWithNinoObjects.generateOne.nino

    val testBaseUrl: String = s"http://$expectedHost:$expectedPort/citizen-details"

    val connector = new CitizenDetailsConnector(httpClient, connectorConfig, appConfig) {
      val testLogger: Logger = logger
    }
  }

  private def testDesignatoryDetails(): JsObject =
    Json.parse("""{"person":{"sex":"F"}}""").as[JsObject]

}
