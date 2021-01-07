/*
 * Copyright 2021 HM Revenue & Customs
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
import support.UnitSpec

class AppConfigSpec extends UnitSpec {

  "returnNinoFromCid" should {
    "default to false" in new Setup {
      appConfig.returnNinoFromCid shouldBe false
    }

    "return false if the config entry is present and set to false" in new Setup {
      override lazy val entries = Map("feature.return-nino-from-cid" -> false)
      appConfig.returnNinoFromCid shouldBe false
    }

    "return true if the config entry is present and set to true" in new Setup {
      override lazy val entries = Map("feature.return-nino-from-cid" -> true)
      appConfig.returnNinoFromCid shouldBe true
    }
  }

  trait Setup {
    lazy val entries: Map[String, Any] = Map()
    lazy val configuration = Configuration.from(entries)
    lazy val appConfig = new AppConfig(configuration)
  }
}
