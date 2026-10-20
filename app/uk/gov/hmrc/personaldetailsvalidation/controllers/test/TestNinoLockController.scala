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
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.{NinoLockConfig, NinoLockRepository}
import uk.gov.hmrc.personaldetailsvalidation.model.NinoLock
import uk.gov.hmrc.personaldetailsvalidation.services.NinoKeyService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class NinoLockTestRequest(nino: String, failedAttempts: Int = 0, locked: Boolean = false)

case class NinoDeleteRequest(nino: String)

@Singleton
class TestNinoLockController @Inject()(ninoKeyService: NinoKeyService,
                                       ninoLockRepository: NinoLockRepository,
                                       ninoLockConfig: NinoLockConfig,
                                       cc: ControllerComponents)
                                      (implicit ec: ExecutionContext)
  extends BackendController(cc) with Logging {

  implicit val ninoLockTestRequestFormat: Format[NinoLockTestRequest] = Json.format[NinoLockTestRequest]
  implicit val ninoDeleteRequestFormat: Format[NinoDeleteRequest] = Json.format[NinoDeleteRequest]

  def upsertNinoLock: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NinoLockTestRequest] { body =>
      withNinoKey(body.nino) { key =>
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val lockedUntil = if (body.locked) Some(now.plus(ninoLockConfig.lockDuration)) else None
        val ninoLock = NinoLock(
          ninoKey = key,
          failedAttempts = body.failedAttempts,
          lastFailedAt = now,
          lockedUntil = lockedUntil,
          expiresAt = lockedUntil.getOrElse(now.plus(ninoLockConfig.lockDuration))
        )
        ninoLockRepository.upsert(ninoLock).map {
          case Right(_) => Created
          case Left(exception) => InternalServerError(exception.getMessage)
        }
      }
    }
  }

  def deleteNinoLock: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NinoDeleteRequest] { body =>
      withNinoKey(body.nino) { key =>
        ninoLockRepository.delete(key).map {
          case Right(_) => NoContent
          case Left(exception) => InternalServerError(exception.getMessage)
        }
      }
    }
  }

  private def withNinoKey(rawNino: String)(f: String => Future[Result]): Future[Result] = {
    val normalised = rawNino.replaceAll("\\s", "").toUpperCase
    if (Nino.isValid(normalised)) f(ninoKeyService.ninoKey(Nino(normalised)))
    else Future.successful(BadRequest("invalid nino"))
  }
}
