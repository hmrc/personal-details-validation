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
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import javax.inject.{Inject, Singleton}
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

case class Retry(credentialId: String, attempts: Option[Int])

object Retry {
  implicit val dateTimeFormats: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val format: OFormat[Retry] = Json.format[Retry]
}


@Singleton
class PersonalDetailsValidationRetryRepository @Inject()(config: PersonalDetailsValidationMongoRepositoryConfig,
                                                         mongoComponent: ReactiveMongoComponent)(implicit ec: ExecutionContext)
  extends ReactiveRepository[Retry, BSONObjectID](
    collectionName = "personal-details-validation-retry-store",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = Retry.format,
    idFormat = ReactiveMongoFormats.objectIdFormats) with TtlIndexedReactiveRepository[Retry, BSONObjectID] {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    super.ensureIndexes.zipWith(maybeCreateTtlIndex)(_ ++ _)
  }

  override val ttl: Long = config.collectionTtl.getSeconds

  //user's CredId is the retry key
  val retryKey = "credentialId"

  def recordAttempt(maybeCredId: String): Future[Done] = {
    import Json.{obj, toJson}
    val selector = obj(retryKey -> maybeCredId)
    val update = obj(
      "$inc" -> obj("attempts" -> 1),
      "$setOnInsert" -> obj(createdAtField -> toJson(DateTime.now.withZone(DateTimeZone.UTC))(Retry.dateTimeFormats))
    )
    findAndUpdate(selector, update, upsert = true, fetchNewObject = true).map(_ => Done).recover{ case _ => Done }
  }

  def getAttempts(maybeCredId: Option[String])(implicit ec: ExecutionContext): EitherT[Future, Exception, Int] = {
    EitherT(
      maybeCredId.fold(Future.successful(Right(0))){ credId =>
        find(retryKey -> credId).map { personalDetailsValidation =>
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
}
