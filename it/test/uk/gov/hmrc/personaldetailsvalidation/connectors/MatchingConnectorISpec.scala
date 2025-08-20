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
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import play.api.http.Status.OK
import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory.{AuditDetailsProvider, AuditTagProvider}
import uk.gov.hmrc.personaldetailsvalidation.audit.{AuditConfig, AuditDataEventFactory}
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.MatchSuccessful
import uk.gov.hmrc.personaldetailsvalidation.matching._
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetailsWithNino
import uk.gov.hmrc.support.utils.BaseIntegrationSpec
import uk.gov.hmrc.support.wiremock.WiremockStubs

class MatchingConnectorISpec extends BaseIntegrationSpec
  with WiremockStubs {

  val auditTagsProvider: AuditTagProvider        = mock[AuditTagProvider]
  val auditDetailsProvider: AuditDetailsProvider = mock[AuditDetailsProvider]
  val auditConfig: AuditConfig                   = new AuditConfig(mock[Configuration]) {
    override lazy val appName: String = "personal-details-validation"
  }

  val connectorConfig: MatchingConnectorConfig = new MatchingConnectorConfig(mock[HostConfigProvider]) {
    override lazy val authenticatorBaseUrl = "http://host/authenticator"

    override def circuitBreakerNumberOfCallsToTrigger: Int = 20
    override def circuitBreakerUnavailableDuration: Int    = 60
    override def circuitBreakerUnstableDuration: Int       = 300
  }

  val auditDataFactory: AuditDataEventFactory = new AuditDataEventFactory(
    auditConfig,
    auditTagsProvider,
    auditDetailsProvider)

  val connector: MatchingConnector = new MatchingConnector(
    mockHttpClient,
    connectorConfig,
    auditDataFactory,
    mockAuditConnector
  )

  val nino: Nino = Nino("AA000003D")
  val ninoWithDifferentSuffix: Nino = Nino("AA000003C")

  val generatedPersonalDetails: PersonalDetailsWithNino = personalDetailsWithNinoObjects.generateOne
  val personalDetails: PersonalDetailsWithNino = generatedPersonalDetails.copy(nino = nino)

  val payload: JsObject = Json.obj(
    "firstName" -> personalDetails.firstName,
    "lastName" -> personalDetails.lastName,
    "dateOfBirth" -> personalDetails.dateOfBirth,
    "nino" -> personalDetails.nino
  )

  "doMatch" should {

    "return MatchSuccessful when POST to authenticator's /authenticator/match returns OK" in {

      val matchingResponsePayload: JsObject = payload + ("nino" -> JsString(ninoWithDifferentSuffix.value))

      stubPostWithRequestAndResponseBody(
        url = "http://host/authenticator/match",
        requestBody = payload,
        expectedResponse = matchingResponsePayload.toString(),
        expectedStatus = OK
      )
//
//      expectPost(toUrl = "http://host/authenticator/match")
//        .withPayload(payload)
//        .returning(OK, matchingResponsePayload)

      connector.doMatch(personalDetails)
        .value.futureValue shouldBe Right(MatchSuccessful(personalDetails.copy(nino = ninoWithDifferentSuffix)))
    }
  }

}
