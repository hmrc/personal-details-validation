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

package uk.gov.hmrc.personaldetailsvalidation.audit

import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory.*
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful, NoLivingMatch}
import uk.gov.hmrc.personaldetailsvalidation.model.*
import uk.gov.hmrc.play.audit.AuditExtensions
import uk.gov.hmrc.play.audit.model.DataEvent

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

@Singleton
class AuditDataEventFactory(auditConfig: AuditConfig, auditTagProvider: AuditTagProvider, auditDetailsProvider: AuditDetailsProvider) {

  @Inject() def this(auditConfig: AuditConfig) = this(
    auditConfig,
    auditTagProvider = (hc, auditType, request) => AuditExtensions.auditHeaderCarrier(hc).toAuditTags(auditType, request.path),
    auditDetailsProvider = hc => AuditExtensions.auditHeaderCarrier(hc).toAuditDetails()
  )

  def createEvent(matchResult: MatchResult, personalDetails: PersonalDetails)
                 (implicit hc: HeaderCarrier, request: Request[?]): DataEvent = createEvent(personalDetails, matchResult.toMatchingStatus, matchResult.otherDetails)

  def createErrorEvent(personalDetails: PersonalDetails)
                      (implicit hc: HeaderCarrier, request: Request[?]): DataEvent = createEvent(personalDetails, "technicalError")

  def createCircuitBreakerEvent(personalDetails: PersonalDetails): DataEvent = {
    val personalVerifier: Map[AuditType, String] = personalDetails match {
      case details: PersonalDetailsNino => Map("unconfirmedNino" -> details.nino.value)
      case details: PersonalDetailsWithPostCode => Map("unconfirmedPostCode" -> details.postCode.value)
    }
    DataEvent(
      auditSource = auditConfig.appName,
      auditType = "CircuitBreakerUnhealthyService",
      tags = Map("transactionName" -> "CircuitBreakerUnhealthyEvent"),
      detail = Map("unavailableServiceName" -> "authenticator") ++ personalVerifier
    )
  }

  private def createEvent(personalDetails: PersonalDetails, matchingStatus: String, otherDetails: Map[String, String] = Map.empty)
                         (implicit hc: HeaderCarrier, request: Request[?]): DataEvent = {
    val nino = personalDetails match {
      case details: PersonalDetailsNino => details.nino.value
      case _ => "NOT SUPPLIED"
    }

    val postCode = personalDetails match {
      case details: PersonalDetailsWithPostCode => details.postCode.value
      case _ => """NOT SUPPLIED"""
    }

    val age = personalDetails match {
      case details: PersonalDetailsWithNino => currentAgeFromDateOfBirth(details.dateOfBirth)
      case details: PersonalDetailsWithPostCode => currentAgeFromDateOfBirth(details.dateOfBirth)
      case _ => """NOT SUPPLIED"""
    }

    DataEvent(
      auditSource = auditConfig.appName,
      auditType = auditType,
      tags = auditTagProvider(hc, auditType, request),
      detail = auditDetailsProvider(hc) + ("nino" -> nino) + ("postCode" -> postCode) + ("age" -> age) + ("matchingStatus" -> matchingStatus) ++ otherDetails
    )
  }

  private def currentAgeFromDateOfBirth(dateOfBirth: LocalDate): String = {
    if (LocalDate.now.getDayOfYear >= dateOfBirth.getDayOfYear - 1) (LocalDate.now.getYear - dateOfBirth.getYear).toString
    else (LocalDate.now.getYear - dateOfBirth.getYear - 1).toString
  }

  private implicit class MatchResultOps(target: MatchResult) {
    def toMatchingStatus: AuditType = target match {
      case MatchSuccessful(_) | NoLivingMatch => "success"
      case MatchFailed(_) => "failed"
    }

    def otherDetails: Map[AuditType, String] = target match {
      case MatchSuccessful(_) | NoLivingMatch => Map.empty[String, String]
      case MatchFailed(errors) => Map("failureDetail" -> errors)
    }
  }

}

private[personaldetailsvalidation] object AuditDataEventFactory {

  type AuditType = String
  type AuditTags = Map[String, String]
  type AuditDetails = Map[String, String]

  type AuditTagProvider = (HeaderCarrier, AuditType, Request[?]) => AuditTags
  type AuditDetailsProvider = HeaderCarrier => AuditDetails

  val auditType: AuditType = "MatchingResult"

}
