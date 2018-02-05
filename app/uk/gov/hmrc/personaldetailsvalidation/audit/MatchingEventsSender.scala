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

import uk.gov.hmrc.audit.{GAEvent, PlatformAnalyticsConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetails

import scala.concurrent.ExecutionContext


@Singleton
private[personaldetailsvalidation] class MatchingEventsSender @Inject()(platformAnalyticsConnector: PlatformAnalyticsConnector) {

  def sendMatchResultEvent(matchResult: MatchResult)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val label = matchResult match {
      case MatchSuccessful(_) => "success"
      case MatchFailed => "failed_matching"
    }

    platformAnalyticsConnector.sendEvent(matchingGaEvent(label))
  }

  def sendMatchingErrorEvent(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    platformAnalyticsConnector.sendEvent(matchingGaEvent("technical_error_matching"))

  def sendSuffixMatchingEvent(externalPerson: PersonalDetails, matchResult: MatchResult)
                             (implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = matchResult match {
    case MatchSuccessful(matchedPerson) if externalPerson.hasSameNinoSuffixAs(matchedPerson) => platformAnalyticsConnector.sendEvent(suffixGaEvent("success_nino_suffix_same_as_cid"))
    case MatchSuccessful(_)  => platformAnalyticsConnector.sendEvent(suffixGaEvent("success_nino_suffix_different_from_cid"))
    case _ =>
  }

  private implicit class PersonalDetailsOps(target: PersonalDetails) {
    def hasSameNinoSuffixAs(other: PersonalDetails): Boolean = target.nino.value.last == other.nino.value.last
  }

  private def matchingGaEvent(label: String) = GAEvent("sos_iv", "personal_detail_validation_result", label)
  private def suffixGaEvent(label: String) = GAEvent("sos_iv", "personal_detail_validation_end", label)

}
