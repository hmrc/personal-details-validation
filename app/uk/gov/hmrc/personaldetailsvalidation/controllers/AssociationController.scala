/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.personaldetailsvalidation.controllers

import play.api.Logging
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat._
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.personaldetailsvalidation.services.{AssociationService, PersonalDetailsValidatorService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class AssociationController @Inject()(associationService: AssociationService,
                                      personalDetailsValidatorService: PersonalDetailsValidatorService,
                                      cc: ControllerComponents
                                     )(implicit ec: ExecutionContext)
  extends BackendController(cc) with Logging {

  def retrieveRecord: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[RetrieveAssociation] {
      case RetrieveAssociation(cred, session) if cred.isBlank || session.isBlank =>
        logger.info("retrieveRecord - credId / sessionId are empty in request")
        Future.successful(BadRequest(errorBody("credentialId / sessionId are empty in request")))
      case retrieveAssociation =>
        associationService.getRecord(retrieveAssociation.credentialId, retrieveAssociation.sessionId).flatMap {
          case None =>
            logger.info("retrieveRecord - no association found for request")
            Future.successful(NotFound(errorBody("No association found")))
          case Some(association) =>
            Try(ValidationId(UUID.fromString(association.validationId))) match {
              case Success(validationId: ValidationId) => personalDetailsValidatorService.getRecord(validationId).map {
                case Some(pdvRecord: PersonalDetailsValidation) =>
                  logger.info(s"retrieveRecord - successfully returned record for ${validationId.value}")
                  Ok(toJson(pdvRecord))
                case None =>
                  logger.warn(s"retrieveRecord - No record found using validation ID ${validationId.value} but should be there if association record exists")
                  NotFound(errorBody(s"No record found using validation ID ${validationId.value}"))
              }
              case _ =>
                logger.error("retrieveRecord - the validationId we store is not a UUID and this should be investigated")
                Future.successful(InternalServerError(errorBody(s"Something went wrong")))
            }
        }
    }
  }

    private def errorBody(errorDescription: String): JsObject = Json.obj("error" -> errorDescription)

}
