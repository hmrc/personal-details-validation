/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory._
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.audit.AuditExtensions
import uk.gov.hmrc.play.audit.model.DataEvent

@Singleton
private[personaldetailsvalidation] class AuditDataEventFactory(auditConfig: AuditConfig, auditTagProvider: AuditTagProvider, auditDetailsProvider: AuditDetailsProvider) {

  @Inject() def this(auditConfig: AuditConfig) = this(
    auditConfig,
    auditTagProvider = (hc, auditType, request) => AuditExtensions.auditHeaderCarrier(hc).toAuditTags(auditType, request.path),
    auditDetailsProvider = hc => AuditExtensions.auditHeaderCarrier(hc).toAuditDetails()
  )

  def createEvent(matchResult: MatchResult, personalDetails: PersonalDetails)
                 (implicit hc: HeaderCarrier, request: Request[_]): DataEvent = createEvent(personalDetails, matchResult.toMatchingStatus, matchResult.otherDetails)

  def createErrorEvent(personalDetails: PersonalDetails)
                      (implicit hc: HeaderCarrier, request: Request[_]): DataEvent = createEvent(personalDetails, "technicalError")

  private def createEvent(personalDetails: PersonalDetails, matchingStatus: String, otherDetails: Map[String, String] = Map.empty)
                         (implicit hc: HeaderCarrier, request: Request[_]): DataEvent = {
    val nino = personalDetails match {
      case details : PersonalDetailsNino => details.nino.value
      case _ => "NOT SUPPLIED"
    }

    val postCode = personalDetails match {
      case details : PersonalDetailsPostCode  => details.postCode.value
      case _ => """NOT SUPPLIED"""
    }

    DataEvent(
      auditSource = auditConfig.appName,
      auditType = auditType,
      tags = auditTagProvider(hc, auditType, request),
      detail = auditDetailsProvider(hc) + ("nino" -> nino) + ("postCode" -> postCode) + ("matchingStatus" -> matchingStatus) ++ otherDetails
    )
  }

  private implicit class MatchResultOps(target: MatchResult) {
    def toMatchingStatus: AuditType = target match {
      case MatchSuccessful(_) => "success"
      case MatchFailed(_) => "failed"
    }

    def otherDetails = target match {
      case MatchSuccessful(_) => Map.empty[String, String]
      case MatchFailed(errors) => Map("failureDetail" -> errors)
    }
  }

}

private[personaldetailsvalidation] object AuditDataEventFactory {

  type AuditType = String
  type AuditTags = Map[String, String]
  type AuditDetails = Map[String, String]

  type AuditTagProvider = (HeaderCarrier, AuditType, Request[_]) => AuditTags
  type AuditDetailsProvider = HeaderCarrier => AuditDetails

  val auditType: AuditType = "MatchingResult"

}