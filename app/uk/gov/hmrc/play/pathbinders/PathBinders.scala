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

package uk.gov.hmrc.play.pathbinders

import java.util.UUID

import play.api.mvc.PathBindable
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetailsValidationId
import uk.gov.voa.valuetype.play.binders.ValueTypePathBinder

import scala.util.Try

object PathBinders extends ValueTypePathBinder {

  object Errors {
    val NOT_A_VALID_UUID = "NOT A VALID UUID"
  }

  import Errors.NOT_A_VALID_UUID

  private val stringToPersonalDetailsValidationId: String => Try[PersonalDetailsValidationId] = str => Try(UUID.fromString(str)).map(PersonalDetailsValidationId(_)).recover {
    case _ => throw new IllegalArgumentException(NOT_A_VALID_UUID)
  }

  implicit val personalDetailsValidationIdBinder: PathBindable[PersonalDetailsValidationId] =
    valueTypeBinder[PersonalDetailsValidationId](stringToPersonalDetailsValidationId)
}
