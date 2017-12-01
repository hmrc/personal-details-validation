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

import akka.Done
import com.google.inject.Inject
import reactivemongo.api.DB
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetailsValidation.personalDetailsValidationFormats
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetailsValidationId.personalDetailsValidationIdFormats

import scala.concurrent.Future


trait PersonalDetailsValidationRepository {
  def create(personalDetails: PersonalDetailsValidation): Future[Done]
  def get(personalDetailsValidationId: PersonalDetailsValidationId): Future[Option[PersonalDetailsValidation]]
}

class PersonalDetailsValidationMongoRepository @Inject()(mongo: () => DB) extends
  ReactiveRepository[PersonalDetailsValidation, PersonalDetailsValidationId](
    "personal-details-validation",
    mongo,
    personalDetailsValidationFormats,
    personalDetailsValidationIdFormats) with PersonalDetailsValidationRepository {


  def create(personalDetails: PersonalDetailsValidation): Future[Done] = ???
  def get(personalDetailsValidationId: PersonalDetailsValidationId): Future[Option[PersonalDetailsValidation]] = ???

}
