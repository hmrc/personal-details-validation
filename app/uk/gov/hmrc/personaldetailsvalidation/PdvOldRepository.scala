/*
 * Copyright 2022 HM Revenue & Customs
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
import cats.data.EitherT
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.mongoEntity
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat.personalDetailsValidationFormats
import uk.gov.hmrc.personaldetailsvalidation.formats.TinyTypesFormats.personalDetailsValidationIdFormats
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, ValidationId}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * This repository contains the old, 7G of PDV journey data that needs to be removed,
 * once all journeys have switched to using the new repository.  The old repository's TTL
 * index was broken, so journey docs were never deleted.  The solution we propose is to move
 * to a new collection (with working TTL), and eventually drop this old collection and remove this class
 */

@Singleton
class PdvOldRepository @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[PersonalDetailsValidation, ValidationId](
    collectionName = "personal-details-validation", // the original collection name (7G of old data)
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = mongoEntity(personalDetailsValidationFormats),
    idFormat = personalDetailsValidationIdFormats
  ) with PdvRepository {

  override def create(personalDetails: PersonalDetailsValidation)(implicit ec: ExecutionContext): EitherT[Future, Exception, Done] =
    throw new RuntimeException("Trying create a journey document in the old PDV collection - this is no longer allowed")

  override def get(personalDetailsValidationId: ValidationId)(implicit ec: ExecutionContext): Future[Option[PersonalDetailsValidation]] =
    findById(personalDetailsValidationId)

}
