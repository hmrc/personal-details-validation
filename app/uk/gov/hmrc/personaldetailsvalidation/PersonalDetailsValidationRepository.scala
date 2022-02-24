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
import com.google.inject.ImplementedBy

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.indexes.IndexType.Descending
import reactivemongo.bson.{BSONDocument, BSONInteger, BSONLong}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.datetime.CurrentTimeProvider
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.mongoEntity
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat._
import uk.gov.hmrc.personaldetailsvalidation.formats.TinyTypesFormats._
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.play.json.ops._

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

private trait PersonalDetailsValidationRepository[Interpretation[_]] {

  def create(personalDetails: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): EitherT[Interpretation, Exception, Done]

  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Interpretation[Option[PersonalDetailsValidation]]
}

@ImplementedBy(classOf[PersonalDetailsValidationMongoRepository])
private trait FuturedPersonalDetailsValidationRepository extends PersonalDetailsValidationRepository[Future]

@Singleton
private class PersonalDetailsValidationMongoRepository @Inject()(config: PersonalDetailsValidationMongoRepositoryConfig,
                                                                 mongoComponent: ReactiveMongoComponent)(implicit currentTimeProvider: CurrentTimeProvider)
  extends ReactiveRepository[PersonalDetailsValidation, ValidationId](
    collectionName = "personal-details-validation",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = mongoEntity(personalDetailsValidationFormats),
    idFormat = personalDetailsValidationIdFormats
  ) with FuturedPersonalDetailsValidationRepository {

  val ttlIndex = "personal-details-validation-ttl-index"
  val OptExpireAfterSeconds = "expireAfterSeconds"
  val createdAtField = "createdAt"

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {

    import reactivemongo.bson.DefaultBSONHandlers._

    val indexes: Future[List[Index]] = collection.indexesManager.list()

    def ensureTtlIndex(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
      Future.sequence(Seq(collection.indexesManager.ensure(
        Index(
          key = Seq(createdAtField -> IndexType.Descending),
          name = Some(ttlIndex),
          options = BSONDocument(OptExpireAfterSeconds -> config.collectionTtl.getSeconds)
        )
      )))
    }

    def ttlHasChanged(index: Index): Boolean =
      !index.options.getAs[BSONLong](OptExpireAfterSeconds).contains(BSONLong(config.collectionTtl.getSeconds))

    indexes.flatMap {
      idxs => {
        val maybeIndex = idxs.find(index => index.eventualName == ttlIndex && ttlHasChanged(index))

        maybeIndex.fold(ensureTtlIndex){ index =>
          collection.indexesManager.drop(index.eventualName).flatMap(_ => ensureTtlIndex)
        }
      }
    }

  }


  override def indexes: Seq[Index] = Seq(
    Index(
      Seq(createdAtField -> Descending),
      name = Some(ttlIndex),
      options = BSONDocument(OptExpireAfterSeconds -> config.collectionTtl.getSeconds))
  )

  def create(personalDetailsValidation: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): EitherT[Future, Exception, Done] = {

    val document = domainFormatImplicit.writes(personalDetailsValidation).as[JsObject].withCreatedTimeStamp(createdAtField)

    EitherT(collection.insert(ordered = false).one(document).map(_ => Right(Done))
      .recover {
        case ex: Exception => Left(ex)
      })
  }

  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Future[Option[PersonalDetailsValidation]] =
    findById(personalDetailsValidationId)

}
