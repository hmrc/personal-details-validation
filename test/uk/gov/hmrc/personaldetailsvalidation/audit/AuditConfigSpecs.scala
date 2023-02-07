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

import play.api.Configuration
import setups.ConfigSetup
import support.UnitSpec

class AuditConfigSpecs extends UnitSpec {

  "appName" should {
    "read value from config if present" in new Setup {
      whenConfigEntriesExists("appName" -> appName) { config =>
        config.appName shouldBe appName
      }
    }

    "throw a runtime exception when there's no value for appName configured" in new Setup {
      whenConfigEntriesExists() { config =>
        a[RuntimeException] should be thrownBy config.appName
      }
    }
  }

  trait Setup extends ConfigSetup[AuditConfig] {
    val appName = "personal-details-validation"
    val newConfigObject: Configuration => AuditConfig = new AuditConfig(_)
  }
}
