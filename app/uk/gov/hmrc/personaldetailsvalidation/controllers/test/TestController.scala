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

package uk.gov.hmrc.personaldetailsvalidation.controllers.test

import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Format, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetailsValidationRepository
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsWithNino, SuccessfulPersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.json.JsonValidation

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestController @Inject()(personalDetailsValidationRepository: PersonalDetailsValidationRepository,
                              cc: ControllerComponents)
                              (implicit ec: ExecutionContext)
  extends BackendController(cc)
    with JsonValidation {

  def upsertTestValidation: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[PersonalDetailsTestValidationData] { personalDetails =>
      val personData = PersonalDetailsWithNino(firstName = "NA - Test Data", lastName = "NA - Test Data", dateOfBirth = LocalDate.now(), nino = personalDetails.nino)
      val validationData = SuccessfulPersonalDetailsValidation(ValidationId(personalDetails.validationId), personalDetails = personData, createdAt = LocalDateTime.now(ZoneOffset.UTC))
      personalDetailsValidationRepository.create(validationData)
      Future.successful(Created)
    }
  }

  implicit val prvtdFormat: Format[PersonalDetailsTestValidationData] = Json.format[PersonalDetailsTestValidationData]
}

case class PersonalDetailsTestValidationData(validationId: UUID, nino: Nino)
