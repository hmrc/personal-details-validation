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
import play.api.mvc.Action
import uk.gov.hmrc.play.bootstrap.controller.BaseController

@Singleton
class PersonalDetailsValidationResourceController extends BaseController {

  def create = Action {
    Created.withHeaders(LOCATION -> routes.PersonalDetailsValidationResourceController.get().url)
  }

  def get = Action {
    Ok("""{"validationStatus":"success"}""")
  }

}
