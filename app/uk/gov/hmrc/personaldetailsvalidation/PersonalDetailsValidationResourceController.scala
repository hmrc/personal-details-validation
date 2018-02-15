/*
 * Copyright 2018 HM Revenue & Customs
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

import cats.implicits._
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc.{Action, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, ValidationId}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.json.JsonValidation
import uk.gov.hmrc.play.json.ops._

import scala.concurrent.Future
import scala.util.Try

@Singleton
class PersonalDetailsValidationResourceController @Inject()(personalDetailsValidationRepository: FuturedPersonalDetailsValidationRepository,
                                                            personalDetailsValidator: FuturedPersonalDetailsValidator)
  extends BaseController
    with JsonValidation {

  import PersonalDetailsValidationResourceController._
  import formats.PersonalDetailsValidationFormat.personalDetailsValidationFormats

  def create = Action.async(parse.json) { implicit request =>
    withJsonBody[PersonalDetails] { personalDetails =>

      def handleMatchingDone(validationId: ValidationId) =
        Future.successful(Created.withHeaders(LOCATION -> routes.PersonalDetailsValidationResourceController.get(validationId).url))

      def handleException(exception: Exception): Future[Result] = Future.failed(exception)

      personalDetailsValidator.validate(personalDetails).fold(handleException, handleMatchingDone).flatten
    }
  }

  def get(id: ValidationId) = Action.async { implicit request =>
    personalDetailsValidationRepository.get(id).map {
      case Some(validation) => Ok(toJson(validation))
      case None => NotFound
    }
  }
}

object PersonalDetailsValidationResourceController {

  private implicit val personalDetailsReads: Reads[PersonalDetails] = (
    (__ \ "firstName").readOrError[String]("firstName is missing").filter(ValidationError("firstName is blank/empty"))(_.trim.nonEmpty) and
      (__ \ "lastName").readOrError[String]("lastName is missing").filter(ValidationError("lastName is blank/empty"))(_.trim.nonEmpty) and
      (__ \ "dateOfBirth").readOrError[LocalDate]("dateOfBirth is missing/invalid") and
      (__ \ "nino").readOrError[String]("nino is missing").map(_.toUpperCase.replaceAll("""\s""", "")).filter(ValidationError("invalid nino format"))(str => Try(Nino(str)).isSuccess).map(Nino)
    ) (PersonalDetails(_, _, _, _))

}
