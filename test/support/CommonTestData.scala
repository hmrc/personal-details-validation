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

package support

import generators.Generators.Implicits.*
import generators.ObjectGenerators.{failedPersonalDetailsValidationObjects, personalDetailsObjects, personalDetailsValidationObjects, successfulPersonalDetailsValidationObjects}
import uk.gov.hmrc.http.BadGatewayException
import uk.gov.hmrc.personaldetailsvalidation.model.*

import java.time.LocalDateTime
import java.util.UUID

trait CommonTestData {

  val testCredId: String            = "cred-123"
  val testSessionId: String         = s"session-${UUID.randomUUID().toString}"
  val testValidationId: String      = UUID.randomUUID().toString
  val sessionId: String             = s"session-${UUID.randomUUID().toString}"
  val testMaybeCredId: Some[String] = Some("test")

  val testOrigin: Some[String]       = Some("test")
  val testLastUpdated: LocalDateTime = LocalDateTime.now()
  val dateTimeNow: LocalDateTime     = LocalDateTime.of(2020, 1, 1, 1, 1)

  val validationId: ValidationId = ValidationId(UUID.fromString("928b39f3-98f7-4a0b-bcfe-9065c1175d1e"))

  val personalDetailsValidationSuccess: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
  val personalDetailsValidationFailure: FailedPersonalDetailsValidation     = failedPersonalDetailsValidationObjects.generateOne
  val personalDetails: PersonalDetails                                      = personalDetailsObjects.generateOne
  val personalDetailsValidation: PersonalDetailsValidation                  = personalDetailsValidationObjects.generateOne

  val exception           = new RuntimeException("error")
  val badGatewayException = new BadGatewayException("some error")

}
