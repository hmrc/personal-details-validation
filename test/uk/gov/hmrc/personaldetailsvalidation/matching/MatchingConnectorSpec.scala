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

package uk.gov.hmrc.personaldetailsvalidation.matching

import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers._
import setups.HttpClientStubSetup
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}

class MatchingConnectorSpec
  extends UnitSpec
    with ScalaFutures
    with MockFactory {

  "doMatch" should {

    "return MatchSuccessful when POST to authenticator's /authenticator/match returns OK" in new Setup {
      expectPost(toUrl = "http://host/authenticator/match")
        .withPayload(payload)
        .returning(OK)

      connector.doMatch(personalDetails).futureValue shouldBe MatchSuccessful
    }

    "return MatchFailed when POST to authenticator's /authenticator/match returns UNAUTHORISED" in new Setup {
      expectPost(toUrl = "http://host/authenticator/match")
        .withPayload(payload)
        .returning(UNAUTHORIZED)

      connector.doMatch(personalDetails).futureValue shouldBe MatchFailed
    }

    Set(NO_CONTENT, NOT_FOUND, INTERNAL_SERVER_ERROR) foreach { unexpectedStatus =>

      s"throws an HttpException when POST to /authenticator/match returns $unexpectedStatus" in new Setup {

        expectPost(toUrl = "http://host/authenticator/match")
          .withPayload(payload)
          .returning(unexpectedStatus, "some response body")

        val exception = intercept[HttpException] {
          await(connector.doMatch(personalDetails))
        }
        exception.message shouldBe s"Unexpected response from POST http://host/authenticator/match with status: '$unexpectedStatus' and body: some response body"
        exception.responseCode shouldBe BAD_GATEWAY
      }
    }
  }

  private trait Setup extends HttpClientStubSetup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val personalDetails = personalDetailsObjects.generateOne
    val payload = Json.obj(
      "firstName" -> personalDetails.firstName,
      "lastName" -> personalDetails.lastName,
      "dateOfBirth" -> personalDetails.dateOfBirth,
      "nino" -> personalDetails.nino
    )

    private val connectorConfig = new MatchingConnectorConfig(mock[Configuration]) {
      override lazy val authenticatorBaseUrl = "http://host/authenticator"
    }
    val connector = new MatchingConnector(httpClient, connectorConfig)
  }
}
