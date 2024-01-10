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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import support.UnitSpec
import uk.gov.hmrc.personaldetailsvalidation.services.{AssociationService, Encryption, PersonalDetailsValidatorService}

class AssociationControllerSpec extends UnitSpec with GuiceOneAppPerSuite {

  "retrieveRecord" should {
    s"return $OK" when {
      "record found" in {

      }
    }
    s"return $NOT_FOUND" when {
      "association record does not exist" in {

      }
      "pdv record does not exist" in {

      }
    }
    s"return $BAD_REQUEST" when {
      "body invalid" in {

      }
    }
    s"return $INTERNAL_SERVER_ERROR" when {
      "service throws exception" in {

      }
      "uuid stored in pdv repo is not valid" in {

      }
    }
  }
}
