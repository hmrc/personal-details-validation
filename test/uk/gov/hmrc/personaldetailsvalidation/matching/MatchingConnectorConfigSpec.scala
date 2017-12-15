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

package uk.gov.hmrc.personaldetailsvalidation.matching

import org.scalamock.scalatest.MockFactory
import play.api.Configuration
import setups.ConfigSetup
import uk.gov.hmrc.play.test.UnitSpec

class MatchingConnectorConfigSpec
  extends UnitSpec
    with MockFactory {

  "authenticatorHost" should {

    "return be comprised of configured host, port and '/authenticator" in new Setup {
      whenConfigEntriesExists(
        "microservice.services.authenticator.host" -> "some-host",
        "microservice.services.authenticator.port" -> "123") { config =>
        config.authenticatorBaseUrl shouldBe "http://some-host:123/authenticator"
      }
    }
    "return be comprised of configured protocol, host and port and '/authenticator" in new Setup {
      whenConfigEntriesExists(
        "microservice.services.authenticator.protocol" -> "some-protocol",
        "microservice.services.authenticator.host" -> "some-host",
        "microservice.services.authenticator.port" -> "123") { config =>
        config.authenticatorBaseUrl shouldBe "some-protocol://some-host:123/authenticator"
      }
    }
    "return be comprised of configured protocol, host and port and '/authenticator when 'microservice.services.protocol' is given" in new Setup {
      whenConfigEntriesExists(
        "microservice.services.protocol" -> "some-protocol",
        "microservice.services.authenticator.host" -> "some-host",
        "microservice.services.authenticator.port" -> "123") { config =>
        config.authenticatorBaseUrl shouldBe "some-protocol://some-host:123/authenticator"
      }
    }
    "throw a runtime exception when there's no value for 'authenticator.host'" in new Setup {
      whenConfigEntriesExists("microservice.services.authenticator.port" -> "123") { config =>
        a[RuntimeException] should be thrownBy config.authenticatorBaseUrl
      }
    }
    "throw a runtime exception when there's no value for 'authenticator.port'" in new Setup {
      whenConfigEntriesExists("microservice.services.authenticator.host" -> "some-host") { config =>
        a[RuntimeException] should be thrownBy config.authenticatorBaseUrl
      }
    }
  }

  private trait Setup extends ConfigSetup[MatchingConnectorConfig] {
    val newConfigObject: Configuration => MatchingConnectorConfig = new MatchingConnectorConfig(_)
  }
}
