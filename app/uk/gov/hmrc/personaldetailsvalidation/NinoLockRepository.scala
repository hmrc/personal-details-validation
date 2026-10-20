/*
 * Copyright 2026 HM Revenue & Customs
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

import org.apache.pekko.Done
import org.bson.BsonNull
import org.mongodb.scala.model.*
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.{MongoCommandException, MongoWriteException}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.personaldetailsvalidation.model.NinoLock

import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NinoLockRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[NinoLock](
    collectionName = "personal-details-validation-nino-lock-store",
    mongoComponent = mongo,
    domainFormat = NinoLock.format,
    indexes = Seq(
      IndexModel(
        keys = ascending("ninoKey"),
        indexOptions = IndexOptions().name("ninoKeyUnique").unique(true)
      ),
      IndexModel(
        keys = ascending("expiresAt"),
        indexOptions = IndexOptions().name("expiresAtTtl").expireAfter(0L, TimeUnit.SECONDS)
      )
    ),
    replaceIndexes = true
  ) {

  private def toDate(t: LocalDateTime): Date = Date.from(t.atZone(ZoneOffset.UTC).toInstant)

  private def isDuplicateKey(ex: Throwable): Boolean = ex match {
    case e: MongoWriteException   => e.getError.getCode == 11000
    case e: MongoCommandException => e.getErrorCode == 11000
    case _                        => false
  }

  def findByNinoKey(ninoKey: String): Future[Either[Exception, Option[NinoLock]]] =
    collection.find(Filters.eq("ninoKey", ninoKey)).headOption()
      .map(result => Right(result): Either[Exception, Option[NinoLock]])
      .recover { case ex: Exception => Left(ex) }

  def incrementFailedAttempts(ninoKey: String, now: LocalDateTime, expiresAt: LocalDateTime, retryOnDuplicateKey: Boolean = true): Future[Either[Exception, NinoLock]] =
    collection.findOneAndUpdate(
      filter = Filters.eq("ninoKey", ninoKey),
      update = Updates.combine(
        Updates.inc("failedAttempts", 1),
        Updates.set("lastFailedAt", toDate(now)),
        Updates.set("expiresAt", toDate(expiresAt)),
        Updates.setOnInsert("firstFailedAt", toDate(now))
      ),
      options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).toFutureOption().map {
      case Some(lock) => Right(lock): Either[Exception, NinoLock]
      case None       => Left(new RuntimeException("incrementFailedAttempts returned no document")): Either[Exception, NinoLock]
    }.recoverWith {
      case ex: Exception if isDuplicateKey(ex) && retryOnDuplicateKey => incrementFailedAttempts(ninoKey, now, expiresAt, retryOnDuplicateKey = false)
      case ex: Exception => Future.successful(Left(ex))
    }

  def createLock(ninoKey: String, lockedUntil: LocalDateTime): Future[Either[Exception, Option[NinoLock]]] =
    collection.findOneAndUpdate(
      filter = Filters.and(Filters.eq("ninoKey", ninoKey), Filters.eq("lockedUntil", BsonNull.VALUE)),
      update = Updates.combine(
        Updates.set("lockedUntil", toDate(lockedUntil)),
        Updates.set("expiresAt", toDate(lockedUntil))
      ),
      options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
    ).toFutureOption()
      .map(result => Right(result): Either[Exception, Option[NinoLock]])
      .recover { case ex: Exception => Left(ex) }

  def delete(ninoKey: String): Future[Either[Exception, Done]] =
    collection.deleteOne(Filters.eq("ninoKey", ninoKey)).toFuture()
      .map(_ => Right(Done): Either[Exception, Done])
      .recover { case ex: Exception => Left(ex) }

  def upsert(ninoLock: NinoLock): Future[Either[Exception, Done]] =
    collection.replaceOne(Filters.eq("ninoKey", ninoLock.ninoKey), ninoLock, ReplaceOptions().upsert(true)).toFuture()
      .map(_ => Right(Done): Either[Exception, Done])
      .recover { case ex: Exception => Left(ex) }
}
