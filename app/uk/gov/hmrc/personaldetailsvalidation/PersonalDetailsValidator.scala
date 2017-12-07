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

import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PersonalDetailsValidator @Inject()(personalDetailsValidationRepository: PersonalDetailsValidationRepository)
                                        (implicit uuidProvider: UUIDProvider) {

  def validate(personalDetails: PersonalDetails)
              (implicit headerCarrier: HeaderCarrier,
               executionContext: ExecutionContext): Future[ValidationId] = {
    val personalDetailsValidation = PersonalDetailsValidation.successful(personalDetails)
    personalDetailsValidationRepository.create(personalDetailsValidation).map { _ =>
      personalDetailsValidation.id
    }
  }
}
