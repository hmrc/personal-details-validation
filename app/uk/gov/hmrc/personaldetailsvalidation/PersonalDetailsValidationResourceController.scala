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

import com.google.inject.Singleton
import org.joda.time.LocalDate
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, _}
import play.api.mvc.Action
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.json.JsonValidation
import uk.gov.hmrc.json.ReadOps._
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

@Singleton
class PersonalDetailsValidationResourceController extends BaseController with JsonValidation {

  private implicit val reads: Reads[PersonalDetails] = (
    (__ \ "firstName").readOrError[String]("firstName is missing") and
      (__ \ "lastName").readOrError[String]("lastName is missing") and
      (__ \ "dateOfBirth").readOrError[LocalDate]("dateOfBirth is missing") and
      (__ \ "nino").readOrError[Nino]("nino is missing")
    ) (PersonalDetails.apply _)

  def create = Action.async(parse.json) { implicit request =>
    withJsonBody[PersonalDetails] { personalDetails =>
      Future.successful(Created.withHeaders(LOCATION -> routes.PersonalDetailsValidationResourceController.get().url))
    }
  }

  def get = Action {
    Ok("""{"validationStatus":"success"}""")
  }

}
