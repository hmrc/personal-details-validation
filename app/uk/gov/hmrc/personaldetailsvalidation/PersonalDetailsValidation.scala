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
import java.util.UUID.randomUUID

import uk.gov.voa.valuetype.ValueType

case class PersonalDetailsValidationId(value: UUID) extends ValueType[UUID]

object PersonalDetailsValidationId {
  def apply(): PersonalDetailsValidationId = PersonalDetailsValidationId(randomUUID())
}

sealed trait ValidationStatus
private case object Success extends ValidationStatus
private case object Failure extends ValidationStatus

case class PersonalDetailsValidation(id: PersonalDetailsValidationId, validationStatus: ValidationStatus, personalDetails: PersonalDetails)

object PersonalDetailsValidation {

  def apply(validationStatus: ValidationStatus, personalDetails: PersonalDetails): PersonalDetailsValidation = PersonalDetailsValidation(PersonalDetailsValidationId(), validationStatus, personalDetails)

  def successfulPersonalDetailsValidation(personalDetails: PersonalDetails) = PersonalDetailsValidation(Success, personalDetails)

  def failedPersonalDetailsValidation(personalDetails: PersonalDetails) = PersonalDetailsValidation(Failure, personalDetails)
}
