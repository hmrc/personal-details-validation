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
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.logger
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.personaldetailsvalidation.model._

import java.time.{LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PersonalDetailsValidationRepository @Inject()(config: PersonalDetailsValidationMongoRepositoryConfig,
                                                    mongo: MongoComponent,
                                                    pdvOldRepository: PdvOldRepository)
                                                   (implicit executionContext: ExecutionContext)
  extends PlayMongoRepository[PersonalDetailsValidationWithCreateTimeStamp](
    collectionName = "pdv-journey", // new collection name
    mongoComponent = mongo,
    domainFormat = PersonalDetailsValidationWithCreateTimeStamp.format,
    indexes = Seq(
      IndexModel(
        ascending("createdAt"),
        indexOptions = IndexOptions().name("expireAfterSeconds").expireAfter(config.collectionTtl.getSeconds, TimeUnit.SECONDS)
      )
    ),
    replaceIndexes = true
  ) with PdvRepository{

  def create(personalDetailsValidation: PersonalDetailsValidation)
            (implicit executionContext: ExecutionContext): EitherT[Future, Exception, Done] = {

    val pdv: PersonalDetailsValidationWithCreateTimeStamp = PersonalDetailsValidationWithCreateTimeStamp(personalDetailsValidation, LocalDateTime.now(ZoneOffset.UTC))

    EitherT(collection.insertOne(pdv).map(_ => Right(Done)).toFuture().map(_ => Right(Done))
      .recover {
        case ex: Exception => Left(ex)
      })
  }

  /**
   * Fetch a journey record by validation id
   *
   * While we are transitioning to the new collection, we need to fallback to looking in the
   * OLD collection for journeys which were started with the previous version of code (only needed for max 24 hours - TTL period)
   */
  def get(personalDetailsValidationId: ValidationId)(implicit executionContext: ExecutionContext): Future[Option[PersonalDetailsValidationWithCreateTimeStamp]] = {
    val completeFilter = Filters.eq("personalDetailsValidation.id", personalDetailsValidationId.value.toString)
    collection.find(completeFilter).toFuture().map(_.headOption)
      .flatMap {
        case Some(result) => Future.successful(Some(result))
        case None =>
          // NOTE: this fallback no longer needed once these messages disappear in production
          logger.warn(s"[VER-1979] Journey with validation id: $personalDetailsValidationId not found, looking in old 7G collection")
          pdvOldRepository.collection.find(completeFilter).toFuture().map(_.headOption) // fallback to old collection
      }
  }

}
