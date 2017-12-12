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

package generators

import org.scalacheck.Gen
import uk.gov.hmrc.personaldetailsvalidation.model.{FailedPersonalDetailsValidation, PersonalDetails, PersonalDetailsValidation, SuccessfulPersonalDetailsValidation}

object ObjectGenerators extends ValueGenerators {

  implicit val personalDetailsObjects: Gen[PersonalDetails] = for {
    firstName <- nonEmptyStrings
    lastName <- nonEmptyStrings
    dateOfBirth <- localDates
    nino <- ninos
  } yield PersonalDetails(firstName, lastName, dateOfBirth, nino)

  implicit val successfulPersonalDetailsValidationObjects: Gen[SuccessfulPersonalDetailsValidation] = for {
    id <- validationIds
    personalDetails <- personalDetailsObjects
  } yield SuccessfulPersonalDetailsValidation(id, personalDetails)

  implicit val failedPersonalDetailsValidationObjects: Gen[FailedPersonalDetailsValidation] =
    validationIds map FailedPersonalDetailsValidation

  implicit val personalDetailsValidationObjects: Gen[PersonalDetailsValidation] = booleans flatMap {
    case true => successfulPersonalDetailsValidationObjects
    case false => failedPersonalDetailsValidationObjects
  }
}