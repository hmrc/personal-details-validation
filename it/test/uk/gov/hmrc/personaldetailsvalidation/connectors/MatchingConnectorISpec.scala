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

import generators.Generators.Implicits._
import generators.ObjectGenerators._
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.circuitbreaker.UnhealthyServiceException
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.BadGatewayException
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.matching._
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetailsWithNino
import uk.gov.hmrc.support.utils.BaseIntegrationSpec
import uk.gov.hmrc.support.wiremock.WiremockStubs

class MatchingConnectorISpec extends BaseIntegrationSpec
  with WiremockStubs {

  lazy val connector: MatchingConnector = app.injector.instanceOf[MatchingConnector]

  val nino: Nino = Nino("AA000003D")
  val ninoWithDifferentSuffix: Nino = Nino("AA000003C")

  val generatedPersonalDetails: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
  val personalDetails: PersonalDetailsWithNino = generatedPersonalDetails.copy(nino = nino)

  val payload: JsObject = Json.obj(
    "firstName"   -> personalDetails.firstName,
    "lastName"    -> personalDetails.lastName,
    "dateOfBirth" -> personalDetails.dateOfBirth,
    "nino"        -> personalDetails.nino
  )

  "doMatch" should {

    "return MatchSuccessful when POST to authenticator's /authenticator/match returns OK" in {

      val matchingResponsePayload: JsObject = payload + ("nino" -> JsString(ninoWithDifferentSuffix.value))

      stubPostWithRequestAndResponseBody(
        url = s"/authenticator/match",
        requestBody = payload,
        expectedResponse = matchingResponsePayload.toString(),
        expectedStatus = OK
      )

      connector.doMatch(personalDetails)
        .value.futureValue mustBe Right(MatchSuccessful(personalDetails.copy(nino = ninoWithDifferentSuffix)))
    }

    "return MatchFailed with errors when POST to authenticator's /authenticator/match returns UNAUTHORISED" in {
      val errors = "Last Name does not match CID"

      stubPostWithRequestAndResponseBody(
        url = s"/authenticator/match",
        requestBody = payload,
        expectedResponse = Json.obj("errors" -> errors).toString(),
        expectedStatus = UNAUTHORIZED
      )

      connector.doMatch(personalDetails).value.futureValue mustBe Right(MatchFailed(errors))
    }

    Set(NO_CONTENT, NOT_FOUND, INTERNAL_SERVER_ERROR) foreach { unexpectedStatus =>

      s"return Left when POST to /authenticator/match returns $unexpectedStatus" in {

        stubPostWithRequestAndResponseBody(
          url = s"/authenticator/match",
          requestBody = payload,
          expectedResponse = "",
          expectedStatus = unexpectedStatus
        )

        val Left(expectedException) = connector.doMatch(personalDetails).value.futureValue
        expectedException mustBe a[BadGatewayException]
        expectedException.getMessage mustBe s"Unexpected response from POST http://localhost:11111/authenticator/match with status: '$unexpectedStatus' and body: "
      }
    }

    "return Left when authenticator went down" in {

      stubPostWithRequestAndResponseBody(
        url = "/authenticator/match",
        requestBody = payload,
        expectedResponse = "some error",
        expectedStatus = 500
      )

      for (_ <- 1 to 20) {
        await(connector.doMatch(personalDetails).value)
      }

      val Left(expectedException) = connector.doMatch(personalDetails).value.futureValue

      expectedException mustBe a[UnhealthyServiceException]
    }
  }
}

