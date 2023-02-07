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

package uk.gov.hmrc.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.config.implicits._
import uk.gov.hmrc.config.ops._
import uk.gov.hmrc.http.Host


@Singleton
class HostConfigProvider @Inject()(configuration: Configuration) {

  def hostFor(serviceName: String): Host = configuration.loadMandatory[Host](serviceName)
  def originDimension: Int = configuration.get[Int]("google-analytics.origin-dimension")
  def ageDimension: Int = configuration.get[Int]("google-analytics.age-dimension")
  def genderDimension: Int = configuration.get[Int]("google-analytics.gender-dimension")

  def circuitBreakerNumberOfCallsToTrigger: Int   = configuration.getOptional[Int]("circuit-breaker.numberOfCallsToTrigger").getOrElse(20)
  def circuitBreakerUnavailableDurationInSec: Int = configuration.getOptional[Int]("circuit-breaker.unavailablePeriodDurationInSec").getOrElse(60)
  def circuitBreakerUnstableDurationInSec: Int    = configuration.getOptional[Int]("circuit-breaker.unstablePeriodDurationInSec").getOrElse(300)
}
