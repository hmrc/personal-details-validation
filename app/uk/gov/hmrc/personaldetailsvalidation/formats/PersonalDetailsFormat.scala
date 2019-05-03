/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.json.ops._

import scala.util.Try

object PersonalDetailsFormat {
  implicit val withNinoFormats: Format[PersonalDetailsWithNino] = Json.format[PersonalDetailsWithNino]
  implicit val withPostCodeFormats: Format[PersonalDetailsWithPostCode] = Json.format[PersonalDetailsWithPostCode]
  private val postCodeValidation = """^([A-Za-z][A-HJ-Ya-hj-y]?[0-9][A-Za-z0-9]?|[A-Za-z][A-HJ-Ya-hj-y][A-Za-z])\s?[0-9][ABDEFGHJLNPQRSTUWXYZabdefghjlnpqrstuwxyz]{2}$""".r

  implicit val personalDetailsReads: Reads[PersonalDetails] = (
    (__ \ "firstName").readOrError[String]("firstName is missing").filter(ValidationError("firstName is blank/empty"))(_.trim.nonEmpty) and
      (__ \ "lastName").readOrError[String]("lastName is missing").filter(ValidationError("lastName is blank/empty"))(_.trim.nonEmpty) and
      (__ \ "dateOfBirth").readOrError[LocalDate]("dateOfBirth is missing/invalid") and
      (
        (__ \ "nino").readNullable[String].map {
          case Some(nino) => Some(nino.toUpperCase.replaceAll("""\s""", ""))
          case _ => None
        }.filter(ValidationError("invalid nino format")) {
          case Some(nino) => Try(Nino(nino)).isSuccess
          case _ => true
        }.map {
          case Some(nino) => Some(Nino(nino))
          case _ => None
        } and
          (__ \ "postCode").readNullable[String].filter(ValidationError("invalid postcode format")) {
            case None => true
            case Some(postCode) if postCodeValidation.findFirstIn(postCode.trim).isEmpty => false
            case _ => true
          }
        ).tupled.
        filter(ValidationError("at least nino or postcode needs to be supplied")) {
          case (None, None) => false
          case _ => true
        }.
        filter(ValidationError("both nino and postcode supplied")) {
          case (Some(_), Some(_)) => false
          case _ => true
        }
    ) ((firstName, lastName, dateOfBirth, ninoOrPostCode) => {
    ninoOrPostCode match {
      case (Some(nino), None) => PersonalDetailsWithNino(firstName, lastName, dateOfBirth, nino)
      case (None, Some(postCode)) => PersonalDetailsWithPostCode(firstName, lastName, dateOfBirth, postCode.trim)
      case _ => throw new IllegalArgumentException("Validation should catch this case")
    }
  })
}
