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

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.personaldetailsvalidation.formats.JavaDateTimeFormatter

import java.time.LocalDateTime

case class NinoLock(
  ninoKey: String,
  failedAttempts: Int,
  lastFailedAt: LocalDateTime,
  lockedUntil: Option[LocalDateTime],
  expiresAt: LocalDateTime
)

object NinoLock extends JavaDateTimeFormatter {
  implicit val dateTimeFormats: Format[LocalDateTime] = localDateTimeFormat
  implicit val format: OFormat[NinoLock] = Json.format[NinoLock]
}
