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

package uk.gov.hmrc.personaldetailsvalidation

import java.util.UUID

import play.api.libs.json._
import uk.gov.hmrc.personaldetailsvalidation.ValidationStatus.{Failure, Success}
import uk.gov.hmrc.uuid.UUIDProvider
import uk.gov.voa.valuetype.play.formats.OptionsFormat
import uk.gov.voa.valuetype.play.formats.ValueTypeFormat.format
import uk.gov.voa.valuetype.{StringOptions, StringValue, ValueType}

case class ValidationId(value: UUID) extends ValueType[UUID]

object ValidationId {

  def apply()(implicit uuidProvider: UUIDProvider): ValidationId = ValidationId(uuidProvider())

  implicit val personalDetailsValidationIdFormats = format[UUID, ValidationId](uuid => ValidationId(uuid))({
    case JsString(value) => UUID.fromString(value)
    case x => throw new IllegalArgumentException(s"Expected a JsString, received $x")
  }, uuid => JsString(uuid.toString))

}

sealed trait ValidationStatus extends StringValue {
  override val value = typeName.toLowerCase
}

object ValidationStatus extends StringOptions[ValidationStatus] with OptionsFormat {
  private [personaldetailsvalidation] case object Success extends ValidationStatus
  private [personaldetailsvalidation] case object Failure extends ValidationStatus

  override val all = Seq(Success, Failure)

  implicit val formats: Format[ValidationStatus] = stringOptionsFormat(this)
}

case class PersonalDetailsValidation(id: ValidationId, validationStatus: ValidationStatus, personalDetails: PersonalDetails)

object PersonalDetailsValidation  {

  def apply(validationStatus: ValidationStatus, personalDetails: PersonalDetails)(implicit uuidProvider: UUIDProvider): PersonalDetailsValidation =
    PersonalDetailsValidation(ValidationId(), validationStatus, personalDetails)

  def successfulPersonalDetailsValidation(personalDetails: PersonalDetails)(implicit uuidProvider: UUIDProvider) = PersonalDetailsValidation(Success, personalDetails)

  def failedPersonalDetailsValidation(personalDetails: PersonalDetails)(implicit uuidProvider: UUIDProvider) = PersonalDetailsValidation(Failure, personalDetails)

  implicit val personalDetailsValidationFormats = Json.format[PersonalDetailsValidation]
}
