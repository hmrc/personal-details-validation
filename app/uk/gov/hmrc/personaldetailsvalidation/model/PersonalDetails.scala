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

package uk.gov.hmrc.personaldetailsvalidation.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit.YEARS
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.model.NonEmptyString

trait PersonalDetails {
  def toJson: JsObject

  def addGender(gender: NonEmptyString): PersonalDetails = this

  def maybeNino: Option[Nino] = None

  def maybeGender:Option[NonEmptyString] = None

  def age: Int

}

trait PersonalDetailsNino {
  def nino: Nino
}

trait PersonalDetailsGender {
  def gender: NonEmptyString
}

trait PersonalDetailsPostCode {
  def postCode: NonEmptyString
}

trait PersonalDetailsDateOfBirth {
  def dateOfBirth: LocalDate

  def age: Int = YEARS.between(dateOfBirth, LocalDate.now()).toInt
}

object PersonalDetails {
  implicit val implicitPersonalDetailsWrite: Writes[PersonalDetails] = new Writes[PersonalDetails] {
    override def writes(details: PersonalDetails): JsValue = {
      details.toJson
    }
  }

  def replacePostCode(detailsWithPostCode: PersonalDetailsWithPostCode, detailsWithNino: PersonalDetailsNino): PersonalDetailsWithNino = {
    PersonalDetailsWithNino(detailsWithPostCode.firstName, detailsWithPostCode.lastName, detailsWithPostCode.dateOfBirth, detailsWithNino.nino)
  }
}


case class PersonalDetailsWithNino(firstName: NonEmptyString,
                                   lastName: NonEmptyString,
                                   dateOfBirth: LocalDate,
                                   nino: Nino) extends PersonalDetails with PersonalDetailsNino with PersonalDetailsDateOfBirth {

  override def addGender(gender: NonEmptyString): PersonalDetails =
    PersonalDetailsWithNinoAndGender(firstName, lastName, dateOfBirth, nino, gender)

  override def maybeNino: Option[Nino] = Some(nino)

  lazy val toJson: JsObject = Json.obj(
    "firstName" -> firstName,
    "lastName" -> lastName,
    "dateOfBirth" -> dateOfBirth,
    "nino" -> nino
  )
}

case class PersonalDetailsWithNinoAndGender(firstName: NonEmptyString,
                                            lastName: NonEmptyString,
                                            dateOfBirth: LocalDate,
                                            nino: Nino,
                                            gender: NonEmptyString) extends PersonalDetails with PersonalDetailsNino with PersonalDetailsGender with PersonalDetailsDateOfBirth {

  override def maybeNino: Option[Nino] = Some(nino)

  override def maybeGender: Option[NonEmptyString] = Some(gender)

  lazy val toJson: JsObject = Json.obj(
    "firstName" -> firstName,
    "lastName" -> lastName,
    "dateOfBirth" -> dateOfBirth,
    "nino" -> nino,
    "gender" -> gender
  )
}

case class PersonalDetailsWithPostCode(firstName: NonEmptyString,
                                       lastName: NonEmptyString,
                                       dateOfBirth: LocalDate,
                                       postCode: NonEmptyString) extends PersonalDetails with PersonalDetailsPostCode with PersonalDetailsDateOfBirth {
  def addNino(nino: Nino): PersonalDetails = {
    PersonalDetailsWithNinoAndPostCode(firstName, lastName, dateOfBirth, nino, postCode)
  }

  lazy val toJson: JsObject = Json.obj(
    "firstName" -> firstName,
    "lastName" -> lastName,
    "dateOfBirth" -> dateOfBirth,
    "postCode" -> postCode.value
  )
}

case class PersonalDetailsWithNinoAndPostCode(firstName: NonEmptyString,
                                              lastName: NonEmptyString,
                                              dateOfBirth: LocalDate,
                                              nino: Nino,
                                              postCode: NonEmptyString)
  extends PersonalDetails
    with PersonalDetailsNino
    with PersonalDetailsPostCode
    with PersonalDetailsDateOfBirth {

  override def addGender(gender: NonEmptyString): PersonalDetails =
    PersonalDetailsWithNinoAndPostCodeAndGender(firstName, lastName, dateOfBirth, nino, postCode, gender)

  override def maybeNino: Option[Nino] = Some(nino)

  lazy val toJson: JsObject = Json.obj(
    "firstName" -> firstName,
    "lastName" -> lastName,
    "dateOfBirth" -> dateOfBirth,
    "postCode" -> postCode.value,
    "nino" -> nino
  )
}

case class PersonalDetailsWithNinoAndPostCodeAndGender(firstName: NonEmptyString,
                                                       lastName: NonEmptyString,
                                                       dateOfBirth: LocalDate,
                                                       nino: Nino,
                                                       postCode: NonEmptyString,
                                                       gender: NonEmptyString)
  extends PersonalDetails
    with PersonalDetailsNino
    with PersonalDetailsPostCode
    with PersonalDetailsGender
    with PersonalDetailsDateOfBirth {

  override def maybeNino: Option[Nino] = Some(nino)

  override def maybeGender: Option[NonEmptyString] = Some(gender)

  lazy val toJson: JsObject = Json.obj(
    "firstName" -> firstName,
    "lastName" -> lastName,
    "dateOfBirth" -> dateOfBirth,
    "postCode" -> postCode.value,
    "nino" -> nino,
    "gender" -> gender
  )
}
