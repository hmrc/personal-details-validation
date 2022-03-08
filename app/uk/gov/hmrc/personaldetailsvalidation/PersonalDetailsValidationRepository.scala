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

import javax.inject.{Inject, Singleton}
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success}

private trait PersonalDetailsValidationRepository[Interpretation[_]] {

  def create(personalDetails: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): EitherT[Interpretation, Exception, Done]

  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Interpretation[Option[PersonalDetailsValidation]]

  def getAttempts(maybeCredId: Option[String])(implicit ec: ExecutionContext): EitherT[Interpretation, Exception, Int]
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
      logger.warn(s"IDX: Ensuring index: $ttlIndex; will create it if not found")
      val ensureF = Future.sequence(Seq(collection.indexesManager.ensure(
        Index(
          key = Seq(createdAtField -> IndexType.Descending),
          name = Some(ttlIndex),
          options = BSONDocument(OptExpireAfterSeconds -> config.collectionTtl.getSeconds)
        )
      )))

      ensureF.onComplete {
        case Failure(exception) => logger.error(s"IDX: Failed to ensure indexes: ${exception.getMessage}", exception)
        case Success(booleans) =>
          logger.warn(s"IDX: Indexes created? " + booleans.mkString(","))
          booleans
      }

      ensureF
    }

    def ttlHasChanged(index: Index): Boolean = {
      logger.warn(s"IDX: ${index.eventualName} has TTL: " + index.options.getAs[BSONLong](OptExpireAfterSeconds))
      logger.warn(s"IDX: Configuration of TTL is: " + BSONLong(config.collectionTtl.getSeconds))
      val changed = !index.options.getAs[BSONLong](OptExpireAfterSeconds).contains(BSONLong(config.collectionTtl.getSeconds))
      logger.warn(s"IDX: TTL has changed? - returning $changed")
      changed
    }

    indexes.flatMap {
      idxs => {

        logger.warn("IDX: Found current indexes: " + idxs.mkString(","))

        val maybeIndex: Option[Index] = idxs.find(index => index.eventualName == ttlIndex && ttlHasChanged(index))

        logger.warn(s"IDX: Found an index with name: $ttlIndex whose existing ttl differs from that in config? " + maybeIndex.isDefined)

        maybeIndex.fold(ensureTtlIndex) { index =>
          logger.warn(s"IDX: Dropping and recreating " + index.eventualName)
          ensureTtlIndex
          //collection.indexesManager.drop(index.eventualName).flatMap(_ => ensureTtlIndex)
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

  //user's CredId is the retry key
  def getAttempts(maybeCredId: Option[String])(implicit ec: ExecutionContext): EitherT[Future, Exception, Int] = {
    EitherT(
      maybeCredId.fold(Future.successful(Right(0))){ credId =>
        find("credentialId" -> credId).map { personalDetailsValidation =>
          personalDetailsValidation.last match {
            case failedPersonalDetailsValidation: FailedPersonalDetailsValidation => Right(failedPersonalDetailsValidation.attempt.getOrElse(0))
            case _ => Right(0)
          }
        }
      }.recover {
        case _: Exception => Right(0)
      }
    )
  }
}
