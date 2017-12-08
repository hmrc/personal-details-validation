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

package uk.gov.hmrc.personaldetailsvalidation.connectors

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetails
import uk.gov.hmrc.personaldetailsvalidation.connectors.MatchingConnector.MatchResult

import scala.concurrent.{ExecutionContext, Future}

class MatchingConnector {

  def doMatch(personalDetails: PersonalDetails)
             (implicit headerCarrier: HeaderCarrier,
              executionContext: ExecutionContext): Future[MatchResult] = ???

}

object MatchingConnector {

  sealed trait MatchResult

  object MatchResult {
    case object MatchSuccessful extends MatchResult
    case object MatchFailed extends MatchResult
  }
}
