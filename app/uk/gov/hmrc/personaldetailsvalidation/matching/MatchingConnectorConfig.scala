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

package uk.gov.hmrc.personaldetailsvalidation.matching

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.config.HostConfigProvider

@Singleton
private class MatchingConnectorConfig @Inject()(hostProvider: HostConfigProvider) {

  lazy val authenticatorBaseUrl: String = hostProvider.hostFor("authenticator") + "/authenticator"

  def circuitBreakerNumberOfCallsToTrigger: Int   = hostProvider.circuitBreakerNumberOfCallsToTrigger
  def circuitBreakerUnavailableDuration: Int = hostProvider.circuitBreakerUnavailableDurationInSec * 1000
  def circuitBreakerUnstableDuration: Int    = hostProvider.circuitBreakerUnstableDurationInSec * 1000
}
