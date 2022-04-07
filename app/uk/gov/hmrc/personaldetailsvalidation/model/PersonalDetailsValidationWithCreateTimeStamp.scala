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

package uk.gov.hmrc.personaldetailsvalidation.model

import play.api.libs.json.{Format, JsNumber, Json, Reads, Writes, __}
import java.time.{Instant, LocalDateTime, ZoneOffset}


case class PersonalDetailsValidationWithCreateTimeStamp(personalDetailsValidation: PersonalDetailsValidation, createdAt: LocalDateTime)

object PersonalDetailsValidationWithCreateTimeStamp {

  implicit val localDateTimeRead: Reads[LocalDateTime] =
    ((__ \ "$date") \ "$numberLong").read[String].map {
      millis =>
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis.toLong), ZoneOffset.UTC)
    }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = (dateTime: LocalDateTime) =>{
    Json.obj(
      "$date" -> JsNumber(dateTime.atZone(ZoneOffset.UTC).toInstant.toEpochMilli)
    )
  }
  import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat._
  implicit val format: Format[PersonalDetailsValidationWithCreateTimeStamp] = Json.format[PersonalDetailsValidationWithCreateTimeStamp]
}
