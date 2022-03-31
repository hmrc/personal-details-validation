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

import java.util.concurrent.TimeUnit

import akka.Done
import cats.data.EitherT
import com.mongodb.client.model.{IndexModel, Updates}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import javax.inject.{Inject, Singleton}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import org.mongodb.scala._
import org.mongodb.scala.model.Aggregates.set
import org.mongodb.scala.model.Filters._

import scala.collection.Seq
import scala.collection.script.Update
import scala.concurrent.{ExecutionContext, Future}

case class Retry(credentialId: String, attempts: Option[Int])

//check original format and find if it is required.
object Retry {
  implicit val dateTimeFormats: Format[DateTime] = Json.format[DateTime]
  implicit val format: OFormat[Retry] = Json.format[Retry]
}

@Singleton
class PersonalDetailsValidationRetryRepository @Inject()(config: PersonalDetailsValidationMongoRepositoryConfig,
                                                         mongo: MongoComponent)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Retry](
    mongoComponent = mongo,
    collectionName = "personal-details-validation-retry-store",
    domainFormat = Retry.format,
    indexes = Seq(IndexModel(
    keys = Indexes.descending("credentialId"),
    indexOptions = IndexOptions().name("credentialIdUnique").unique(true).expireAfter(config.collectionTtl.getSeconds, TimeUnit.SECONDS)
  ))) with TtlIndexedReactiveRepository[Retry] {


  //user's CredId is the retry key
  lazy val retryKey = "credentialId"

  def recordAttempt(maybeCredId: String): Future[Done] = {
    import Json.toJson
    val update = Updates.set(
      "$inc" , ("attempts", 1),
      "$setOnInsert" , (createdAtField, toJson(DateTime.now.withZone(DateTimeZone.UTC))(Retry.dateTimeFormats))
    )
    collection.findOneAndUpdate(Filters.eq(retryKey, maybeCredId), Filters.eq(update)).map(_ => Done).recover{ case _ => Done }.toFuture()
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
