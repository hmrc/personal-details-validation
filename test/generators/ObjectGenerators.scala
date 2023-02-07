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

package generators

import org.scalacheck.Gen
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.audit.model.DataEvent
import PostCodeGenerator._

import java.time.{LocalDateTime, ZoneOffset}

object ObjectGenerators extends ValueGenerators {

  implicit val personalDetailsWithNinoObjects: Gen[PersonalDetailsWithNino] = for {
    firstName <- nonEmptyStrings
    lastName <- nonEmptyStrings
    dateOfBirth <- localDates
    nino <- ninos
  } yield PersonalDetailsWithNino(firstName, lastName, dateOfBirth, nino)

  implicit val personalDetailsWithNinoGenderObjects: Gen[PersonalDetailsWithNinoAndGender] = for {
    firstName <- nonEmptyStrings
    lastName <- nonEmptyStrings
    dateOfBirth <- localDates
    nino <- ninos
    gender <- nonEmptyStrings
  } yield PersonalDetailsWithNinoAndGender(firstName, lastName, dateOfBirth, nino, gender)

  implicit val personalDetailsWithPostCodeObjects: Gen[PersonalDetailsWithPostCode] = for {
    firstName <- nonEmptyStrings
    lastName <- nonEmptyStrings
    dateOfBirth <- localDates
    postCode <- postCode
  } yield PersonalDetailsWithPostCode(firstName, lastName, dateOfBirth, postCode)

  implicit val personalDetailsObjects: Gen[PersonalDetails] = personalDetailsWithNinoObjects.map(_.asInstanceOf[PersonalDetails])

  implicit val successfulPersonalDetailsValidationObjects: Gen[SuccessfulPersonalDetailsValidation] = for {
    id <- validationIds
    personalDetails <- personalDetailsWithNinoObjects
  } yield SuccessfulPersonalDetailsValidation(id, "success", personalDetails, LocalDateTime.now(ZoneOffset.UTC))

  implicit val failedPersonalDetailsValidationObjects: Gen[FailedPersonalDetailsValidation] =
    validationIds.map{id =>
      FailedPersonalDetailsValidation(id, "failure", maybeCredId, attempt, LocalDateTime.now(ZoneOffset.UTC))
    }

  implicit val personalDetailsValidationObjects: Gen[PersonalDetailsValidation] = booleans flatMap {
    case true => successfulPersonalDetailsValidationObjects
    case false => failedPersonalDetailsValidationObjects
  }

  implicit val dataEvents: Gen[DataEvent] = for {
    auditSource <- nonEmptyStrings.map(_.value)
    auditType <- nonEmptyStrings.map(_.value)
  } yield DataEvent(auditSource, auditType)
}