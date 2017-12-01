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

import com.google.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, _}
import play.api.mvc.Action
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.json.JsonValidation
import uk.gov.hmrc.json.ReadOps._
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.voa.valuetype.play.formats.ValueTypeFormat

@Singleton
class PersonalDetailsValidationResourceController @Inject()(personalDetailsValidationRepository: PersonalDetailsValidationRepository) extends BaseController with JsonValidation with ValueTypeFormat {

  private implicit val personalDetailsReads: Reads[PersonalDetails] = (
    (__ \ "firstName").readOrError[String]("firstName is missing") and
      (__ \ "lastName").readOrError[String]("lastName is missing") and
      (__ \ "dateOfBirth").readOrError[LocalDate]("dateOfBirth is missing") and
      (__ \ "nino").readOrError[Nino]("nino is missing")
    ) (PersonalDetails.apply _)

  private implicit val validationStatusWrites = new Writes[ValidationStatus] {
    override def writes(o: ValidationStatus) = JsString(o.getClass.getSimpleName.dropRight(1).toLowerCase)
  }
  private implicit val personalDetailsValidationIdWrites = valueTypeWritesFor[UUID, PersonalDetailsValidationId](uuid => JsString(uuid.toString))
  private implicit val personalDetailsWrites = Json.writes[PersonalDetails]
  private implicit val personalDetailsValidationWrites = Json.writes[PersonalDetailsValidation]

  def create = Action.async(parse.json) { implicit request =>
    withJsonBody[PersonalDetails] { personalDetails =>
      personalDetailsValidationRepository.create(personalDetails).map { validation =>
        Created.withHeaders(LOCATION -> routes.PersonalDetailsValidationResourceController.get(validation.id).url)
      }
    }
  }

  def get(id: PersonalDetailsValidationId) = Action.async { implicit request =>
    personalDetailsValidationRepository.get(id).map { validation => Ok(Json.toJson(validation)) }
  }
}
