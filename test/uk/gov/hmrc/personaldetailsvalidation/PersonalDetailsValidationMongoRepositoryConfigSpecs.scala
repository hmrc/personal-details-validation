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

package uk.gov.hmrc.personaldetailsvalidation

import play.api.Configuration
import setups.ConfigSetup
import support.UnitSpec

import java.time.Duration
import java.time.format.DateTimeParseException

class PersonalDetailsValidationMongoRepositoryConfigSpecs extends UnitSpec {

  "config" should {
    "return ttl duration from configuration" in new Setup {
      whenConfigEntriesExists("mongodb.collections.personal-details-validation.ttl" -> "P1D"){ config =>
        config.collectionTtl shouldBe Duration.ofDays(1)
      }
    }

    "throw DateTimeParseException if configured ttl is not a valid ISO date time syntax" in new Setup {
      whenConfigEntriesExists("mongodb.collections.personal-details-validation.ttl" -> "P1 D"){ config =>
        a[DateTimeParseException] should be thrownBy config.collectionTtl
      }
    }

    "throw RuntimeException if ttl is not configured" in new Setup {
      whenConfigEntriesExists(){ config =>
        a[RuntimeException] should be thrownBy config.collectionTtl
      }
    }
  }

  private trait Setup extends ConfigSetup[PersonalDetailsValidationMongoRepositoryConfig] {
    val newConfigObject: Configuration => PersonalDetailsValidationMongoRepositoryConfig = new PersonalDetailsValidationMongoRepositoryConfig(_)
  }
}
