/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.personaldetailsvalidation.controllers.test

import play.api.Logging
import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetailsValidationRetryRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

case class CredIdAttemptsRequest(credId: String, attempts: Int)

case class CredIdDeleteRequest(credId: String)

@Singleton
class TestCredIdRetryController @Inject()(retryRepository: PersonalDetailsValidationRetryRepository,
                                          cc: ControllerComponents)
                                         (implicit ec: ExecutionContext)
  extends BackendController(cc) with Logging {

  implicit val credIdAttemptsRequestFormat: Format[CredIdAttemptsRequest] = Json.format[CredIdAttemptsRequest]
  implicit val credIdDeleteRequestFormat: Format[CredIdDeleteRequest] = Json.format[CredIdDeleteRequest]

  def setCredIdAttempts(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CredIdAttemptsRequest] { body =>
      retryRepository.upsertAttempts(body.credId, body.attempts).map {
        case Right(_) => Created
        case Left(exception) => InternalServerError(exception.getMessage)
      }
    }
  }

  def deleteCredIdAttempts(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CredIdDeleteRequest] { body =>
      retryRepository.deleteAttempts(body.credId).map {
        case Right(_) => NoContent
        case Left(exception) => InternalServerError(exception.getMessage)
      }
    }
  }
}
