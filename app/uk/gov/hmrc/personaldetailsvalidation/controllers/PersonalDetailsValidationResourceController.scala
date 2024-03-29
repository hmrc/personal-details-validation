/*
 * Copyright 2023 HM Revenue & Customs
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
import cats.implicits._
import play.api.Logging
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.credentials
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.personaldetailsvalidation.services.PersonalDetailsValidatorService
import uk.gov.hmrc.personaldetailsvalidation.{PersonalDetailsValidationRetryRepository, PersonalDetailsValidator, formats}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.json.JsonValidation

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PersonalDetailsValidationResourceController @Inject()(personalDetailsValidatorService: PersonalDetailsValidatorService,
                                                            personalDetailsValidationRetryRepository: PersonalDetailsValidationRetryRepository,
                                                            personalDetailsValidator: PersonalDetailsValidator,
                                                            cc: ControllerComponents)
                                                           (implicit val authConnector: AuthConnector, ec: ExecutionContext)
  extends BackendController(cc) with JsonValidation with AuthorisedFunctions with Logging {

  import formats.PersonalDetailsFormat._
  import formats.PersonalDetailsValidationFormat.personalDetailsValidationFormats

  def create(origin: Option[String] = None): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[PersonalDetails] { personalDetails =>
      def handleMatchingDone(personalDetailsValidation: PersonalDetailsValidation): Future[Result] = {
        Future.successful(Created(toJson(personalDetailsValidation))
          .withHeaders(LOCATION -> routes.PersonalDetailsValidationResourceController.get(personalDetailsValidation.id).url))
      }
      def handleException(exception: Exception): Future[Result] = Future.failed(exception)

      lazy val toAuthCredentialId: Option[Credentials] => Future[Option[String]] = (credentials: Option[Credentials]) => Future.successful(credentials.map(_.providerId))
      val credentialId: Future[Option[String]] = authorised().retrieve(credentials)(toAuthCredentialId).recover{case _ => None}
      credentialId.flatMap { maybeCredId =>
        personalDetailsValidator.validate(personalDetails, origin, maybeCredId).fold(handleException, handleMatchingDone).flatten
      }
    }
  }

  def getUserAttempts: Action[AnyContent] = Action.async { implicit request =>
    lazy val toAuthCredentialId: Option[Credentials] => Future[Option[String]] = (credentials: Option[Credentials]) => Future.successful(credentials.map(_.providerId))
    val attempts: EitherT[Future, Exception, Result] = for {
      maybeCredId <- EitherT.right(authorised().retrieve(credentials)(toAuthCredentialId).recover{case _ => None})
      attempts <- personalDetailsValidationRetryRepository.getAttempts(maybeCredId)
    } yield {
      Ok(Json.toJson(UserAttemptsDetails(attempts, maybeCredId)))
    }

    attempts.value match {
      case value => value.map {
        case Right(httpResult) => httpResult
        case _ => Ok(Json.toJson(UserAttemptsDetails(0, None)))
      }
    }
  }

  def get(id: ValidationId): Action[AnyContent] = Action.async {
    personalDetailsValidatorService.getRecord(id).map {
      case Some(validation) => Ok(toJson(validation))
      case None => NotFound
    }
  }
}
