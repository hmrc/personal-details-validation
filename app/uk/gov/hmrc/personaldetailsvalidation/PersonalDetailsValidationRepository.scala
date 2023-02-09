/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mongodb.scala._
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat
import uk.gov.hmrc.personaldetailsvalidation.model._

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PersonalDetailsValidationRepository @Inject()(config: PersonalDetailsValidationMongoRepositoryConfig,
                                                    mongoComponent: MongoComponent)(implicit executionContext: ExecutionContext)
  extends PlayMongoRepository[PersonalDetailsValidation](
    collectionName = "pdv-journey",
    mongoComponent = mongoComponent,
    domainFormat = PersonalDetailsValidationFormat.personalDetailsValidationFormats,
    indexes = Seq(
      IndexModel(
        ascending("createdAt"),
        indexOptions = IndexOptions().name("expireAfterSeconds").expireAfter(config.collectionTtl.getSeconds, TimeUnit.SECONDS)
      ),
      IndexModel(
        ascending("id"), // primary key for fetch
        indexOptions = IndexOptions().name("pk_id")
      )
    ),
    replaceIndexes = false,
    extraCodecs = Seq(Codecs.playFormatCodec(PersonalDetailsValidationFormat.SuccessfulPersonalDetailsValidationFormat), Codecs.playFormatCodec(PersonalDetailsValidationFormat.FailedPersonalDetailsValidationFormat))
  ) with PdvRepository with Logging {

  // On startup, we add a hook to drop the OLD collection (if exists)
  // Once collection is successfully dropped (check Grafana) this code can be removed:
  //
  mongoComponent.database.listCollectionNames().toFuture().map { collectionNames =>
    val oldCollectionName =  "personal-details-validation"
    if (collectionNames.contains(oldCollectionName)) {
      logger.warn("[VER-2047] Dropping old personal-details-validation collection...")
      mongoComponent.database.getCollection(oldCollectionName).drop().toFuture()
    }
  }

  def create(personalDetailsValidation: PersonalDetailsValidation)(implicit executionContext: ExecutionContext): EitherT[Future, Exception, Done] = {

    EitherT(
      collection.insertOne(personalDetailsValidation).toFuture()
        .map(_ => Right(Done))
        .recover {
          case ex: Exception => Left(ex)
        }
    )
  }

  /**
   * Fetch a journey record by validation id
   */
  def get(personalDetailsValidationId: ValidationId)(implicit executionContext: ExecutionContext): Future[Option[PersonalDetailsValidation]] = {
    val completeFilter = Filters.eq("id", personalDetailsValidationId.value.toString)
    collection.find(completeFilter).toFuture().map(_.headOption)
  }

}
