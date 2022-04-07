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
import org.mongodb.scala.model.Filters
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, PersonalDetailsValidationWithCreateTimeStamp, ValidationId}

import javax.inject.{Inject, Singleton}
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

/**
 * This repository contains the old, 7G of PDV journey data that needs to be removed,
 * once all journeys have switched to using the new repository.  The old repository's TTL
 * index was broken, so journey docs were never deleted.  The solution we propose is to move
 * to a new collection (with working TTL), and eventually drop this old collection and remove this class
 */

@Singleton
class PdvOldRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[PersonalDetailsValidationWithCreateTimeStamp](
    mongoComponent = mongo,
    collectionName = "personal-details-validation", // the original collection name (7G of old data)
    domainFormat = PersonalDetailsValidationWithCreateTimeStamp.format,
    indexes = Seq(),
    replaceIndexes = true
  ) with PdvRepository {

  override def create(personalDetails: PersonalDetailsValidation)(implicit ec: ExecutionContext): EitherT[Future, Exception, Done] =
    throw new RuntimeException("Trying create a journey document in the old PDV collection - this is no longer allowed")

  override def get(personalDetailsValidationId: ValidationId)(implicit ec: ExecutionContext): Future[Option[PersonalDetailsValidationWithCreateTimeStamp]] = {
    val completeFilter = Filters.and(Filters.eq("_id_", personalDetailsValidationId))
    collection.find(completeFilter).toFuture().map(_.headOption)
  }

}
