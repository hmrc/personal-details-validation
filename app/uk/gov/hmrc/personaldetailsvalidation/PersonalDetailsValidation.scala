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

import uk.gov.hmrc.uuid.UUIDProvider
import uk.gov.voa.valuetype.{StringOptions, StringValue, ValueType}

case class ValidationId(value: UUID) extends ValueType[UUID]

object ValidationId {

  def apply()(implicit uuidProvider: UUIDProvider): ValidationId = ValidationId(uuidProvider())
}

sealed trait ValidationStatus extends StringValue {
  override val value: String = typeName.toLowerCase
}

object ValidationStatus extends StringOptions[ValidationStatus] {
  private[personaldetailsvalidation] case object Success extends ValidationStatus
  private[personaldetailsvalidation] case object Failure extends ValidationStatus

  override val all: Seq[ValidationStatus] = Seq(Success, Failure)
}

sealed trait PersonalDetailsValidation {
  val id: ValidationId
}

case class SuccessfulPersonalDetailsValidation(id: ValidationId,
                                               personalDetails: PersonalDetails)
  extends PersonalDetailsValidation

case class FailedPersonalDetailsValidation(id: ValidationId)
  extends PersonalDetailsValidation

object PersonalDetailsValidation {

  def successful(personalDetails: PersonalDetails)
                (implicit uuidProvider: UUIDProvider) =
    SuccessfulPersonalDetailsValidation(ValidationId(), personalDetails)

  def failed()(implicit uuidProvider: UUIDProvider) =
    FailedPersonalDetailsValidation(ValidationId())
}
