/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.Configuration
import setups.ConfigSetup
import support.UnitSpec
import uk.gov.hmrc.http.Host

class HostConfigProviderSpecs extends UnitSpec {

  "baseUrl" should {

    "return be comprised of configured host, port" in new Setup {
      whenConfigEntriesExists(
        s"microservice.services.$serviceName.host" -> "some-host",
        s"microservice.services.$serviceName.port" -> "123") { config =>
        config.hostFor(serviceName) shouldBe Host("http://some-host:123")
      }
    }

    "return be comprised of configured protocol, host and port and '/$serviceName" in new Setup {
      whenConfigEntriesExists(
        s"microservice.services.$serviceName.protocol" -> "some-protocol",
        s"microservice.services.$serviceName.host" -> "some-host",
        s"microservice.services.$serviceName.port" -> "123") { config =>
        config.hostFor(serviceName) shouldBe Host("some-protocol://some-host:123")
      }
    }

    "return be comprised of configured protocol, host and port 'microservice.services.protocol' is given" in new Setup {
      whenConfigEntriesExists(
        s"microservice.services.protocol" -> "some-protocol",
        s"microservice.services.$serviceName.host" -> "some-host",
        s"microservice.services.$serviceName.port" -> "123") { config =>
        config.hostFor(serviceName) shouldBe Host("some-protocol://some-host:123")
      }
    }
    "throw a runtime exception when there's no value for '$serviceName.host'" in new Setup {
      whenConfigEntriesExists(s"microservice.services.$serviceName.port" -> "123") { config =>
        a[RuntimeException] should be thrownBy config.hostFor(serviceName)
      }
    }
    "throw a runtime exception when there's no value for '$serviceName.port'" in new Setup {
      whenConfigEntriesExists(s"microservice.services.$serviceName.host" -> "some-host") { config =>
        a[RuntimeException] should be thrownBy config.hostFor(serviceName)
      }
    }
  }

  private trait Setup extends ConfigSetup[HostConfigProvider] {
    val serviceName = "some-service"
    val newConfigObject: Configuration => HostConfigProvider = new HostConfigProvider(_)
  }

}
