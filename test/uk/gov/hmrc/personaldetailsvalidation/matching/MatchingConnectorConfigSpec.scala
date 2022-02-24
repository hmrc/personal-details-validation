/*
 * Copyright 2022 HM Revenue & Customs
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

import org.scalamock.scalatest.MockFactory
import support.UnitSpec
import uk.gov.hmrc.config.HostConfigProvider
import uk.gov.hmrc.http.Host

class MatchingConnectorConfigSpec
  extends UnitSpec
    with MockFactory {

  "authenticatorBaseUrl" should {

    "be created using HostConfigProvider" in new Setup {
      val hostValue = "http://localhost:9000"
      hostProvider.hostFor _ expects "authenticator" returning Host(hostValue)
      config.authenticatorBaseUrl shouldBe s"$hostValue/authenticator"
    }
  }

  private trait Setup {
    val hostProvider = mock[HostConfigProvider]
    val config = new MatchingConnectorConfig(hostProvider)
  }

}
