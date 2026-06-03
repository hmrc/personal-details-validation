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
import play.api.libs.json.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.personaldetailsvalidation.formats.JavaDateTimeFormatter
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, SuccessfulPersonalDetailsValidation}

import java.time.{LocalDateTime, ZoneOffset}
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

  def recordAttempt(maybeCredId: String, attempts: Int = 0): Future[Either[Exception, Done]] = {
    val update = Retry(maybeCredId, Some(attempts + 1))
    collection.replaceOne(Filters.eq(retryKey, maybeCredId), update, ReplaceOptions().upsert(true)).toFuture().map(_ => Right(Done))
      .recover {
        case ex: Exception => Left(ex)
      }
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
          case _ => getAttempts(Some(credId)).flatMap {
            case Right(attempts) => recordAttempt(credId, attempts)
            case Left(exception) => Future.successful(Left(exception))
          }
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
