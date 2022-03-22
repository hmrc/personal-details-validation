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

package uk.gov.hmrc.personaldetailsvalidation.matching

import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import setups.HttpClientStubSetup
import support.UnitSpec
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier}
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class MatchingConnectorSpec
  extends UnitSpec
    with ScalaFutures
    with MockFactory {

  "doMatch" should {

    "return MatchSuccessful when POST to authenticator's /authenticator/match returns OK" in new Setup {

      val matchingResponsePayload = payload + ("nino" -> JsString(ninoWithDifferentSuffix.value))

      expectPost(toUrl = "http://host/authenticator/match")
        .withPayload(payload)
        .returning(OK, matchingResponsePayload)

      connector.doMatch(personalDetails)
        .value.futureValue shouldBe Right(MatchSuccessful(personalDetails.copy(nino = ninoWithDifferentSuffix)))
    }

    "return MatchFailed with errors when POST to authenticator's /authenticator/match returns UNAUTHORISED" in new Setup {
      val errors = "Last Name does not match CID"
      expectPost(toUrl = "http://host/authenticator/match")
        .withPayload(payload)
        .returning(UNAUTHORIZED, Json.obj("errors" -> errors))

      connector.doMatch(personalDetails).value.futureValue shouldBe Right(MatchFailed(errors))
    }

    Set(NO_CONTENT, NOT_FOUND, INTERNAL_SERVER_ERROR) foreach { unexpectedStatus =>

      s"return Left when POST to /authenticator/match returns $unexpectedStatus" in new Setup {

        expectPost(toUrl = "http://host/authenticator/match")
          .withPayload(payload)
          .returning(unexpectedStatus, "some response body")

        val Left(expectedException) = connector.doMatch(personalDetails).value.futureValue
        expectedException shouldBe a[BadGatewayException]
        expectedException.getMessage shouldBe s"Unexpected response from POST http://host/authenticator/match with status: '$unexpectedStatus' and body: some response body"
      }
    }

    "return Left when POST to /authenticator/match returns a failed Future" in new Setup {

      val exception = new RuntimeException("some error")

      expectPost(toUrl = "http://host/authenticator/match")
        .withPayload(payload)
        .throwing(exception)

      connector.doMatch(personalDetails).value.futureValue shouldBe Left(exception)
    }
  }

  private trait Setup extends HttpClientStubSetup {

    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val nino = Nino("AA000003D")
    val ninoWithDifferentSuffix = Nino("AA000003C")
    val generatedPersonalDetails = personalDetailsWithNinoObjects.generateOne
    val personalDetails = generatedPersonalDetails.copy(nino = nino)
    val payload = Json.obj(
      "firstName" -> personalDetails.firstName,
      "lastName" -> personalDetails.lastName,
      "dateOfBirth" -> personalDetails.dateOfBirth,
      "nino" -> personalDetails.nino
    )

    private val connectorConfig = new MatchingConnectorConfig(mock[HostConfigProvider]) {
      override lazy val authenticatorBaseUrl = "http://host/authenticator"
    }

    val connector = new MatchingConnectorImpl(httpClient, connectorConfig)

    implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5 seconds, interval = 100 millis)
  }
}
