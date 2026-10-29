/*
 * Copyright 2025 HM Revenue & Customs
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

import cats.data.EitherT
import cats.implicits.*
import play.api.Logging
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsResultException, JsValue, Json}
import play.api.mvc.*
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.credentials
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, NoActiveSession}
import uk.gov.hmrc.personaldetailsvalidation.model.*
import uk.gov.hmrc.personaldetailsvalidation.services.PersonalDetailsValidatorService
import uk.gov.hmrc.personaldetailsvalidation.{PersonalDetailsValidationRetryRepository, PersonalDetailsValidator, formats}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PersonalDetailsValidationResourceController @Inject()(personalDetailsValidatorService: PersonalDetailsValidatorService,
                                                            personalDetailsValidationRetryRepository: PersonalDetailsValidationRetryRepository,
                                                            personalDetailsValidator: PersonalDetailsValidator,
                                                            cc: ControllerComponents)
                                                           (implicit val authConnector: AuthConnector, ec: ExecutionContext)
  extends BackendController(cc) with AuthorisedFunctions with Logging {

  import formats.PersonalDetailsFormat.*
  import formats.PersonalDetailsValidationFormat.personalDetailsValidationFormats

  def create(origin: Option[String] = None): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[PersonalDetails] { personalDetails =>
      def handleMatchingDone(personalDetailsValidation: PersonalDetailsValidation): Future[Result] = {
        Future.successful(Created(toJson(personalDetailsValidation))
          .withHeaders(LOCATION -> routes.PersonalDetailsValidationResourceController.get(personalDetailsValidation.id).url))
      }
      def handleException(exception: Exception): Future[Result] = Future.failed(exception)

      credentialId.flatMap { maybeCredId =>
        personalDetailsValidator.validate(personalDetails, origin, maybeCredId).fold(handleException, handleMatchingDone).flatten
      }
    }
  }

  def getUserAttempts: Action[AnyContent] = Action.async { implicit request =>
    val attempts: EitherT[Future, Exception, Result] = for {
      maybeCredId <- EitherT.right(credentialId)
      attempts <- personalDetailsValidationRetryRepository.getAttemptsT(maybeCredId)
    } yield {
      Ok(Json.toJson(UserAttemptsDetails(attempts, maybeCredId)))
    }

    attempts.value.flatMap {
      case Right(httpResult) => Future.successful(httpResult)
      case Left(exception)   => Future.failed(exception)
    }
  }

  private def credentialId(implicit request: Request[?]): Future[Option[String]] =
    authorised().retrieve(credentials) { (credentials: Option[Credentials]) =>
      Future.successful(credentials.map(_.providerId))
    }.recover {
      case _: NoActiveSession   => None
      case _: JsResultException => None
    }

  def get(id: ValidationId): Action[AnyContent] = Action.async {
    personalDetailsValidatorService.getRecord(id).map {
      case Some(validation) => Ok(toJson(validation))
      case None => NotFound
    }
  }
}
