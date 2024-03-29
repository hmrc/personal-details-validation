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

import javax.inject.{Inject, Singleton}
import play.api.mvc.Request
import uk.gov.hmrc.audit.{GAEvent, PlatformAnalyticsConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful, NoLivingMatch}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext

@Singleton
private[personaldetailsvalidation] class EventsSender @Inject()(platformAnalyticsConnector: PlatformAnalyticsConnector,
                                                                auditConnector: AuditConnector,
                                                                auditDataFactory: AuditDataEventFactory) {

  def sendEvents(matchResult: MatchResult, personalDetails: PersonalDetails, origin: Option[String])(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Unit = {

    matchResult match {
      case NoLivingMatch => //todo: any event?
      case _ => sendGAMatchResultEvent(matchResult, origin)
        sendGAMatchResultForNinoOrPostcodeEvent(matchResult, personalDetails, origin)
        sendGASuffixMatchingEvent(matchResult, personalDetails, origin)
        auditConnector.sendEvent(auditDataFactory.createEvent(matchResult, personalDetails))
    }

  }

  private def sendGAMatchResultEvent(matchResult: MatchResult, origin: Option[String])(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    matchResult match {
      case MatchSuccessful(matchPd) => platformAnalyticsConnector.sendEvent(gaEvent("success"), origin, Some(matchPd))
      case MatchFailed(_) => platformAnalyticsConnector.sendEvent(gaEvent("failed_matching"), origin)
    }
  }

  private def sendGAMatchResultForNinoOrPostcodeEvent(matchResult: MatchResult, personalDetails: PersonalDetails, origin: Option[String])(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    (matchResult, personalDetails) match {
      case (MatchSuccessful(matchPd), _: PersonalDetailsWithNino) => platformAnalyticsConnector.sendEvent(gaEvent("success_withNINO"), origin, Some(matchPd))
      case (MatchSuccessful(matchPd), _: PersonalDetailsWithNinoAndGender) => platformAnalyticsConnector.sendEvent(gaEvent("success_withNINO"), origin, Some(matchPd))
      case (MatchSuccessful(matchPd), _: PersonalDetailsWithPostCode) => platformAnalyticsConnector.sendEvent(gaEvent("success_withPOSTCODE"), origin, Some(matchPd))
      case (MatchFailed(_), _: PersonalDetailsWithNino) => platformAnalyticsConnector.sendEvent(gaEvent("failed_matching_withNINO"), origin)
      case (MatchFailed(_), _: PersonalDetailsWithNinoAndGender) => platformAnalyticsConnector.sendEvent(gaEvent("failed_matching_withNINO"), origin)
      case (MatchFailed(_), _: PersonalDetailsWithPostCode) => platformAnalyticsConnector.sendEvent(gaEvent("failed_matching_withPOSTCODE"), origin)
    }
  }

  private def sendGASuffixMatchingEvent(matchResult: MatchResult, externalPerson: PersonalDetails, origin: Option[String])
                                       (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Unit = (matchResult, externalPerson) match {
    case (MatchSuccessful(matchPd: PersonalDetailsWithNino), _) if externalPerson.hasSameNinoSuffixAs(matchPd) => platformAnalyticsConnector.sendEvent(gaEvent("success_nino_suffix_same_as_cid"), origin, Some(matchPd))
    case (MatchSuccessful(matchPd: PersonalDetailsWithNinoAndGender), _) if externalPerson.hasSameNinoSuffixAs(matchPd) => platformAnalyticsConnector.sendEvent(gaEvent("success_nino_suffix_same_as_cid"), origin, Some(matchPd))
    case (MatchSuccessful(matchPd), _: PersonalDetailsWithNino) => platformAnalyticsConnector.sendEvent(gaEvent("success_nino_suffix_different_from_cid"), origin, Some(matchPd))
    case (MatchSuccessful(matchPd), _: PersonalDetailsWithNinoAndGender) => platformAnalyticsConnector.sendEvent(gaEvent("success_nino_suffix_different_from_cid"), origin, Some(matchPd))
    case (MatchSuccessful(matchPd), _: PersonalDetailsWithPostCode) => platformAnalyticsConnector.sendEvent(gaEvent("success_postcode_suffix"), origin, Some(matchPd))
    case _ =>
  }

  def sendErrorEvents(personalDetails: PersonalDetails, origin: Option[String])(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Unit = {
    platformAnalyticsConnector.sendEvent(gaEvent("technical_error_matching"), origin)
    auditConnector.sendEvent(auditDataFactory.createErrorEvent(personalDetails))
  }

  def sentCircuitBreakerEvent(personalDetails: PersonalDetails)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    platformAnalyticsConnector.sendEvent(GAEvent("sos_iv", "circuit_breaker", "pdv_unavailable_circuit-breaker"), None, Some(personalDetails))
    auditConnector.sendEvent(auditDataFactory.createCircuitBreakerEvent(personalDetails))
  }

  private implicit class PersonalDetailsOps(target: PersonalDetails) {
    def hasSameNinoSuffixAs(other: PersonalDetails): Boolean = {
      (target, other) match {
        case (first: PersonalDetailsNino, second: PersonalDetailsNino) => first.nino.value == second.nino.value
        case _ => false
      }
    }
  }

  private def gaEvent(label: String) = GAEvent("sos_iv", "personal_detail_validation_result", label)

}
