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
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
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

@Singleton
class PersonalDetailsValidationRepository @Inject()(config: PersonalDetailsValidationMongoRepositoryConfig,
                                                    mongoComponent: ReactiveMongoComponent,
                                                    pdvOldRepository: PdvOldRepository)(implicit currentTimeProvider: CurrentTimeProvider)
  extends ReactiveRepository[PersonalDetailsValidation, ValidationId](
    collectionName = "pdv-journey", // new collection name
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = mongoEntity(personalDetailsValidationFormats),
    idFormat = personalDetailsValidationIdFormats
  ) with PdvRepository with TtlIndexedReactiveRepository[PersonalDetailsValidation, ValidationId] {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {

   // TODO on startup, AFTER journeys are all using the new collection, add a hook to drop the OLD collection (if exists)
   //   Once collection is successfully dropped (check Grafana) the hook can be removed:
   //    mongo()
   //      .collection[JSONCollection]("personal-details-validation")
   //      .drop(failIfNotFound = false)

    super.ensureIndexes.zipWith(maybeCreateTtlIndex)(_ ++ _)
  }

  override val ttl: Long = config.collectionTtl.getSeconds

  def create(personalDetailsValidation: PersonalDetailsValidation)
            (implicit ec: ExecutionContext): EitherT[Future, Exception, Done] = {

    val document: JsObject =
      domainFormatImplicit.writes(personalDetailsValidation).as[JsObject].withCreatedTimeStamp(createdAtField)

    EitherT(collection.insert(ordered = false).one(document).map(_ => Right(Done))
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
  def get(personalDetailsValidationId: ValidationId)
         (implicit ec: ExecutionContext): Future[Option[PersonalDetailsValidation]] = {
    findById(personalDetailsValidationId)
      .flatMap {
        case Some(result) => Future.successful(Some(result))
        case None =>
          // NOTE: this fallback no longer needed once these messages disappear in production
          logger.warn(s"[VER-1979] Journey with validation id: $personalDetailsValidationId not found, looking in old 7G collection")
          pdvOldRepository.findById(personalDetailsValidationId) // fallback to old collection
      }
  }

}
