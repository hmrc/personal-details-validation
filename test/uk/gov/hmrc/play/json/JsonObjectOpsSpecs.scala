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

package uk.gov.hmrc.play.json

import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

import org.scalamock.scalatest.MockFactory
import play.api.libs.json.Json
import support.UnitSpec
import uk.gov.hmrc.datetime.CurrentTimeProvider

class JsonObjectOpsSpecs extends UnitSpec with MockFactory {

  "withCreatedTimeStamp" should {
    "timestamp with default fieldName 'createdAt' to provided jsobject" in new Setup {
      implicit val timeProvider: CurrentTimeProvider = stub[CurrentTimeProvider]

      val currentTime: LocalDateTime = LocalDateTime.now()

      (() => timeProvider.apply()).expects().returning(currentTime)
      Json.obj("foo" -> "bar")
        .withCreatedTimeStamp("createdAt") shouldBe Json.obj(
        "foo" -> "bar",
        "createdAt" -> Json.obj("$date" -> currentTime.atZone(UTC).toInstant.toEpochMilli)
      )
    }

    "timestamp with provided fieldName to provided jsobject" in new Setup {
      implicit val timeProvider: CurrentTimeProvider = stub[CurrentTimeProvider]

      val currentTime: LocalDateTime = LocalDateTime.now()

      (() => timeProvider.apply()).expects().returning(currentTime)
      Json.obj("foo" -> "bar")
        .withCreatedTimeStamp("timestamp") shouldBe Json.obj(
        "foo" -> "bar",
        "timestamp" -> Json.obj("$date" -> currentTime.atZone(UTC).toInstant.toEpochMilli))
    }
  }

  trait Setup extends JsonObjectOps {

  }

}
