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

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, _}
import play.api.mvc.Action
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.json.JsonValidation
import uk.gov.hmrc.play.json.ReadOps._
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetailsValidation.successfulPersonalDetailsValidation
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.uuid.UUIDProvider
import uk.gov.voa.valuetype.play.formats.ValueTypeFormat

@Singleton
class PersonalDetailsValidationResourceController @Inject()
(personalDetailsValidationRepository: PersonalDetailsValidationRepository)
(implicit uuidProvider: UUIDProvider)
  extends BaseController with JsonValidation with ValueTypeFormat {

  private implicit val personalDetailsReads: Reads[PersonalDetails] = (
    (__ \ "firstName").readOrError[String]("firstName is missing") and
      (__ \ "lastName").readOrError[String]("lastName is missing") and
      (__ \ "dateOfBirth").readOrError[LocalDate]("dateOfBirth is missing") and
      (__ \ "nino").readOrError[Nino]("nino is missing")
    ) (PersonalDetails.apply _)

  def create = Action.async(parse.json) { implicit request =>
    withJsonBody[PersonalDetails] { personalDetails =>
      val personalDetailsValidation = successfulPersonalDetailsValidation(personalDetails)
      personalDetailsValidationRepository.create(personalDetailsValidation).map { _ =>
        Created.withHeaders(LOCATION -> routes.PersonalDetailsValidationResourceController.get(personalDetailsValidation.id).url)
      }
    }
  }

  def get(id: ValidationId) = Action.async { implicit request =>
    personalDetailsValidationRepository.get(id).map {
      case Some(validation) => Ok(Json.toJson(validation))
      case None => NotFound
    }
  }
}
