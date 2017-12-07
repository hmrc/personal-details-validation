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

import akka.Done
import com.google.inject.ImplementedBy
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.mongoEntity
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat._
import uk.gov.hmrc.personaldetailsvalidation.formats.TinyTypesFormats._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PersonalDetailsValidationMongoRepository])
trait PersonalDetailsValidationRepository {

  def create(personalDetails: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): Future[Done]

  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Future[Option[PersonalDetailsValidation]]
}

@Singleton
class PersonalDetailsValidationMongoRepository @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[PersonalDetailsValidation, ValidationId](
    collectionName = "personal-details-validation",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = mongoEntity(personalDetailsValidationFormats),
    idFormat = personalDetailsValidationIdFormats
  ) with PersonalDetailsValidationRepository {

  def create(personalDetailsValidation: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): Future[Done] =
    insert(personalDetailsValidation).map(_ => Done)

  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Future[Option[PersonalDetailsValidation]] =
    findById(personalDetailsValidationId)
}
