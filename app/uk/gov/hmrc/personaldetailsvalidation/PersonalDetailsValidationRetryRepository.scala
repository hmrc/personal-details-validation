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
import com.mongodb.client.model.Updates
import org.bson.conversions.Bson
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala._
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

case class Retry(credentialId: String, attempts: Option[Int])

object Retry {
  implicit val dateTimeFormats: Format[DateTime] = {
    implicit val dateTimeRead: Reads[DateTime] =
      ((__ \ "$date") \ "$numberLong").read[Long].map { dateTime =>
        new DateTime(dateTime, DateTimeZone.UTC)
      }

    implicit val dateTimeWrite: Writes[DateTime] = new Writes[DateTime] {
      def writes(dateTime: DateTime): JsValue = Json.obj(
        "$date" -> dateTime.getMillis
      )
    }
    Format(dateTimeRead, dateTimeWrite)
  }
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
        keys = Indexes.descending("credentialId"),
        indexOptions = IndexOptions().name("credentialIdUnique").unique(true).expireAfter(config.collectionTtl.getSeconds, TimeUnit.SECONDS)
      )
    )
  )  {


  //user's CredId is the retry key
  lazy val retryKey = "credentialId"

  def recordAttempt(maybeCredId: String): Future[Done] = {
    val update: Bson = Updates.inc("attempts", 1)
    collection.findOneAndUpdate(Filters.eq(retryKey, maybeCredId), update).map(_ => Done).recover{ case _ => Done }.toFuture().map(_ => Done)
  }

  def getAttempts(maybeCredId: Option[String])(implicit ec: ExecutionContext): EitherT[Future, Exception, Int] = EitherT(
    maybeCredId.fold(Future.successful(Right(0))){ credId =>
      val completeFilter = Filters.and(Filters.eq("_id_", credId))
      collection.find(completeFilter).toFuture().map { personalDetailsValidation =>
        personalDetailsValidation.last match {
          case maybeRetry: Retry => Right(maybeRetry.attempts.getOrElse(0))
          case _ => Right(0)
        }
      }
    }.recover {
      case _: Exception => Right(0)
    }
  )
}
