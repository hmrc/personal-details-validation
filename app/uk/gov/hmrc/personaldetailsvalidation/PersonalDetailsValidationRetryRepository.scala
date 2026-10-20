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

import cats.data.EitherT
import org.apache.pekko.Done
import org.mongodb.scala.model.*
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.{MongoCommandException, MongoWriteException}
import play.api.libs.json.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.personaldetailsvalidation.formats.JavaDateTimeFormatter
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, SuccessfulPersonalDetailsValidation}

import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class Retry(credentialId: String, attempts: Option[Int], createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC))

object Retry extends JavaDateTimeFormatter {
  implicit val dateTimeFormats: Format[LocalDateTime] = localDateTimeFormat
  implicit val format: OFormat[Retry] = Json.format[Retry]
}


@Singleton
class PersonalDetailsValidationRetryRepository @Inject()(config: PersonalDetailsValidationMongoRepositoryConfig,
                                                         mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Retry](
    collectionName = "personal-details-validation-retry-store",
    mongoComponent = mongo,
    domainFormat = Retry.format,
    indexes = Seq(
      IndexModel(
        ascending("createdAt"),
        indexOptions = IndexOptions().name("expireAfterSeconds").expireAfter(config.collectionTtl.getSeconds, TimeUnit.SECONDS)
      ),
      IndexModel(
        keys = Indexes.descending("credentialId"),
        indexOptions = IndexOptions().name("credentialIdUnique").unique(true)
      )
    ),
    replaceIndexes = true
  )  {

  //user's CredId is the retry key
  lazy val retryKey = "credentialId"

  private def toDate(t: LocalDateTime): Date = Date.from(t.atZone(ZoneOffset.UTC).toInstant)

  private def isDuplicateKey(ex: Throwable): Boolean = ex match {
    case e: MongoWriteException   => e.getError.getCode == 11000
    case e: MongoCommandException => e.getErrorCode == 11000
    case _                        => false
  }

  /** Atomic $inc; upserts when no CredID retry document exists; returns the new attempt count (VER-7058). */
  def incrementAttempts(credId: String, retryOnDuplicateKey: Boolean = true): Future[Either[Exception, Int]] =
    collection.findOneAndUpdate(
      filter = Filters.eq(retryKey, credId),
      update = Updates.combine(
        Updates.inc("attempts", 1),
        Updates.set("createdAt", toDate(LocalDateTime.now(ZoneOffset.UTC)))
      ),
      options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).toFutureOption().map {
      case Some(retry) => Right(retry.attempts.getOrElse(1)): Either[Exception, Int]
      case None        => Right(1): Either[Exception, Int]
    }.recoverWith {
      case ex: Exception if isDuplicateKey(ex) && retryOnDuplicateKey => incrementAttempts(credId, retryOnDuplicateKey = false)
      case ex: Exception => Future.successful(Left(ex))
    }

  def deleteAttempts(credId: String): Future[Either[Exception, Done]] = {
    collection.deleteOne(Filters.eq(retryKey, credId)).toFuture().map(_ => Right(Done))
      .recover {
        case ex: Exception => Left(ex)
      }
  }

  def getAttempts(maybeCredId:Option[String])(implicit ec: ExecutionContext): Future[Either[Exception, Int]] = {
    maybeCredId.fold(Future.successful(Right(0))){ credId =>
      val completeFilter = Filters.and(Filters.eq(retryKey, credId))
      collection.find(completeFilter).toFuture().map { personalDetailsValidation =>
        if(personalDetailsValidation.isEmpty) Right(0) else Right(personalDetailsValidation.last.attempts.getOrElse(0))
      }.recover {
        case ex: Exception => Left(ex)
      }
    }
  }

  private def updateRetryAttempts(maybeCredId: Option[String], personalDetailsValidation: PersonalDetailsValidation)(implicit ec: ExecutionContext): Future[Either[Exception, Done]] = {
    maybeCredId match {
      case Some(credId) =>
        personalDetailsValidation match {
          case _: SuccessfulPersonalDetailsValidation => deleteAttempts(credId)
          case _ => incrementAttempts(credId).map(_.map(_ => Done))
        }
      case None => Future.successful(Right(Done))
    }
  }

  def updateRetryAttemptsT(maybeCredId: Option[String], personalDetailsValidation: PersonalDetailsValidation)(implicit ec: ExecutionContext): EitherT[Future, Exception, Done] = {
    EitherT(updateRetryAttempts(maybeCredId, personalDetailsValidation)(using ec))
  }

  def getAttemptsT(maybeCredId: Option[String])(implicit ec: ExecutionContext): EitherT[Future, Exception, Int] = {
    EitherT(getAttempts(maybeCredId)(using ec))
  }

}
