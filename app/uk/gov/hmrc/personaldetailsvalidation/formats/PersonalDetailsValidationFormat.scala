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

package uk.gov.hmrc.personaldetailsvalidation.formats

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.personaldetailsvalidation._
import uk.gov.hmrc.personaldetailsvalidation.model._

import java.time.{LocalDateTime, ZoneOffset}


object PersonalDetailsValidationFormat {

  import PersonalDetailsInternalFormat.repositoryPersonalDetailsReads
  import TinyTypesFormats._
  import model.ValidationStatus._

  implicit val dateTimeFormats: Format[LocalDateTime] = MongoJavatimeFormats.localDateTimeFormat

  implicit val SuccessfulPersonalDetailsValidationFormat: OFormat[SuccessfulPersonalDetailsValidation] = Json.format[SuccessfulPersonalDetailsValidation]

  implicit val FailedPersonalDetailsValidationFormat: OFormat[FailedPersonalDetailsValidation] = Json.format[FailedPersonalDetailsValidation]

  implicit val personalDetailsValidationFormats: Format[PersonalDetailsValidation] = {

    implicit class JsonOps(json: JsValue) {

      lazy val toSuccessfulPersonalDetailsValidation: JsResult[SuccessfulPersonalDetailsValidation] = (
        (json \ "id").validate[ValidationId] and
          (json \ "personalDetails").validate[PersonalDetails] and
          ((json \ "createdAt").validate[LocalDateTime] or
            Reads.pure[LocalDateTime](LocalDateTime.now(ZoneOffset.UTC)).reads(Json.toJson(LocalDateTime.now(ZoneOffset.UTC))))
        ) ((id, pd, dt) => SuccessfulPersonalDetailsValidation(id, "success", pd, dt))

      lazy val toFailedPersonalDetailsValidation: JsResult[FailedPersonalDetailsValidation] =
        ((json \ "id").validate[ValidationId] and
          (json \ "credentialId").validateOpt[String] and
          (json \ "attempts").validateOpt[Int] and
          ((json \ "createdAt").validate[LocalDateTime] or
            Reads.pure[LocalDateTime](LocalDateTime.now(ZoneOffset.UTC)).reads(Json.toJson(LocalDateTime.now(ZoneOffset.UTC))))
          )((id, credentialId, attempts, dt) => FailedPersonalDetailsValidation(id, "failure", credentialId, attempts, dt))
    }

    val reads: Reads[PersonalDetailsValidation] = Reads[PersonalDetailsValidation] { json =>
      (json \ "validationStatus").validate[ValidationStatus] flatMap {
        case Success => json.toSuccessfulPersonalDetailsValidation
        case Failure => json.toFailedPersonalDetailsValidation
      }
    }

    val writes: Writes[PersonalDetailsValidation] = Writes[PersonalDetailsValidation] {
      case SuccessfulPersonalDetailsValidation(id, _, personalDetails: PersonalDetails, createdAt: LocalDateTime) => Json.obj(
        "id" -> id,
        "validationStatus" -> Success.value,
        "personalDetails" -> personalDetails,
        "createdAt" -> createdAt
      )
      case FailedPersonalDetailsValidation(id, _, maybeCredId, attempt, createdAt: LocalDateTime) => Json.obj(
        "id" -> id,
        "credentialId" -> maybeCredId,
        "validationStatus" -> Failure.value,
        "attempts" -> attempt,
        "createdAt" -> createdAt
      )
    }

    Format(reads, writes)
  }
}
