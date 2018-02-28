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
import uk.gov.hmrc.audit.{GAEvent, PlatformAnalyticsConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetails
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext

@Singleton
private[personaldetailsvalidation] class EventsSender @Inject()(platformAnalyticsConnector: PlatformAnalyticsConnector,
                                                                auditConnector: AuditConnector,
                                                                auditDataFactory: AuditDataEventFactory) {

  def sendEvents(matchResult: MatchResult, personalDetails: PersonalDetails)(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Unit = {
    sendGAMatchResultEvent(matchResult)
    sendGASuffixMatchingEvent(matchResult, personalDetails)
    auditConnector.sendEvent(auditDataFactory.createEvent(matchResult, personalDetails))
  }

  private def sendGAMatchResultEvent(matchResult: MatchResult)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val label = matchResult match {
      case MatchSuccessful(_) => "success"
      case MatchFailed => "failed_matching"
    }

    platformAnalyticsConnector.sendEvent(gaEvent(label))
  }

  private def sendGASuffixMatchingEvent(matchResult: MatchResult, externalPerson: PersonalDetails)
                                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = matchResult match {
    case MatchSuccessful(matchedPerson) if externalPerson.hasSameNinoSuffixAs(matchedPerson) => platformAnalyticsConnector.sendEvent(gaEvent("success_nino_suffix_same_as_cid"))
    case MatchSuccessful(_) => platformAnalyticsConnector.sendEvent(gaEvent("success_nino_suffix_different_from_cid"))
    case _ =>
  }

  def sendErrorEvents(personalDetails: PersonalDetails)(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Unit = {
    platformAnalyticsConnector.sendEvent(gaEvent("technical_error_matching"))
    auditConnector.sendEvent(auditDataFactory.createErrorEvent(personalDetails))
  }

  private implicit class PersonalDetailsOps(target: PersonalDetails) {
    def hasSameNinoSuffixAs(other: PersonalDetails): Boolean = {
      (target.nino, other.nino) match {
        case (Some(first), Some(second)) if first.value == second.value => true
        case _ => false
      }
    }
  }

  private def gaEvent(label: String) = GAEvent("sos_iv", "personal_detail_validation_result", label)

}
