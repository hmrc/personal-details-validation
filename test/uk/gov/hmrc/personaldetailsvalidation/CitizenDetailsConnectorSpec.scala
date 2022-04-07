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

package uk.gov.hmrc.personaldetailsvalidation

import generators.Generators.Implicits._
import generators.ObjectGenerators.personalDetailsWithNinoObjects
import org.scalatest.LoneElement
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.concurrent.Eventually
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{JsObject, Json}
import setups.HttpClientStubSetup
import support.UnitSpec
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class CitizenDetailsConnectorSpec
  extends UnitSpec with ScalaCheckDrivenPropertyChecks with HttpClientStubSetup
    with LoneElement with Eventually {

  "Get designatory details" should {
    "details are returned with gender" in new Setup {
        expectGet(toUrl = s"$testBaseUrl/$nino/designatory-details")
          .returning(200, testDesignatoryDetails())

        connector.findDesignatoryDetails(nino).futureValue shouldBe Some(Gender("F"))
    }
  }

    private trait Setup {
    val expectedHost = "localhost"
    val expectedPort = 9337

    val hostConfigProvider = mock[HostConfigProvider]
    lazy val connectorConfig = new CitizenDetailsConnectorConfig(hostConfigProvider){
      override lazy val baseUrl = testBaseUrl
    }

    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val nino = personalDetailsWithNinoObjects.generateOne.nino

    val testBaseUrl: String = s"http://$expectedHost:$expectedPort/citizen-details"

    val connector = new CitizenDetailsConnector(httpClient, connectorConfig)
  }

  private def testDesignatoryDetails(): JsObject =
    Json.parse("""{"person":{"sex":"F"}}""").as[JsObject]

}
