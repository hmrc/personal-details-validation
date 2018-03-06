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

import javax.inject.{Inject, Singleton}

import cats.implicits._
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc.{Action, Result}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.json.JsonValidation

import scala.concurrent.Future

@Singleton
class PersonalDetailsValidationResourceController @Inject()(personalDetailsValidationRepository: FuturedPersonalDetailsValidationRepository,
                                                            personalDetailsValidator: FuturedPersonalDetailsValidator)
  extends BaseController
    with JsonValidation {

  import formats.PersonalDetailsValidationFormat.personalDetailsValidationFormats
  import formats.PersonalDetailsExternalFormat.personalDetailsReads

  def create = Action.async(parse.json) { implicit request =>
    withJsonBody[PersonalDetails] { personalDetails =>

      def handleMatchingDone(validationId: ValidationId) = {
        Future.successful(Created.withHeaders(LOCATION -> routes.PersonalDetailsValidationResourceController.get(validationId).url))
      }

      def handleException(exception: Exception): Future[Result] = {
        exception match {
          case ex : IllegalArgumentException =>
            val errors = JsArray(Seq(JsString(ex.getMessage)))
            Future.successful(BadRequest(JsObject(Map("errors" -> errors))))
          case _ => Future.failed(exception)
        }

      }

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
