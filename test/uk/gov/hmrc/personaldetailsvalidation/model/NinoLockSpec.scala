/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.personaldetailsvalidation.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDateTime

class NinoLockSpec extends AnyWordSpec with Matchers {

  private val now = LocalDateTime.of(2026, 1, 1, 12, 0)
  private val record = NinoLock("AA000003", 3, now.minusHours(1), Some(now), now)

  "isLocked" should {
    "return true before the lock expires" in {
      record.isLocked(now.minusNanos(1)) shouldBe true
    }

    "return false exactly when the lock expires" in {
      record.isLocked(now) shouldBe false
    }

    "return false after the lock expires" in {
      record.isLocked(now.plusNanos(1)) shouldBe false
    }

    "return false when no lock is set" in {
      record.copy(lockedUntil = None).isLocked(now) shouldBe false
    }
  }
}
