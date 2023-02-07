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

package uk.gov.hmrc.personaldetailsvalidation.model

import uk.gov.hmrc.uuid.UUIDProvider
import uk.gov.voa.valuetype.{StringOptions, StringValue, ValueType}

import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID

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
                                               validationStatus: String = "success",
                                               personalDetails: PersonalDetails,
                                               createdAt: LocalDateTime,
                                               deceased: Boolean = false)
  extends PersonalDetailsValidation

case class FailedPersonalDetailsValidation(id: ValidationId,
                                           validationStatus: String = "failure",
                                           maybeCredId: Option[String] = None,
                                           attempt: Option[Int] = None,
                                           createdAt: LocalDateTime)
  extends PersonalDetailsValidation

object PersonalDetailsValidation {

  def successful(personalDetails: PersonalDetails, createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC), deceased: Boolean = false)
                (implicit uuidProvider: UUIDProvider): SuccessfulPersonalDetailsValidation =
    SuccessfulPersonalDetailsValidation(ValidationId(), personalDetails = personalDetails, createdAt = createdAt, deceased = deceased)

  def failed(maybeCredId: Option[String], attempts: Option[Int], createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC))(implicit uuidProvider: UUIDProvider): FailedPersonalDetailsValidation =
    FailedPersonalDetailsValidation(ValidationId(), maybeCredId = maybeCredId, attempt = attempts, createdAt = createdAt)
}
