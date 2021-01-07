/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.LocalDate

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.model._

import scala.util.Try

object PersonalDetailsInternalFormat {
  implicit val repositoryPersonalDetailsReads: Reads[PersonalDetails] = (
    (__ \ "firstName").read[String] and
      (__ \ "lastName").read[String] and
      (__ \ "dateOfBirth").read[LocalDate] and
      (
        (__ \ "nino").readNullable[String].filter(JsonValidationError("invalid nino format")) {
          case Some(nino) => Try(Nino(nino)).isSuccess
          case _ => true
        }.map {
          case Some(nino) => Some(Nino(nino))
          case _ => None
        } and
          (__ \ "postCode").readNullable[String]
        ).tupled
    ) ((firstName, lastName, dateOfBirth, ninoOrPostCode) => {
    ninoOrPostCode match {
      case (Some(nino), None) => PersonalDetailsWithNino(firstName, lastName, dateOfBirth, nino)
      case (None, Some(postCode)) => PersonalDetailsWithPostCode(firstName, lastName, dateOfBirth, postCode)
      case (Some(nino), Some(postCode)) => PersonalDetailsWithNinoAndPostCode(firstName, lastName, dateOfBirth, nino, postCode)
      case _ => throw new IllegalArgumentException("We should never have saved one of these")
    }
  })

}
