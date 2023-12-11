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

import com.google.inject.ImplementedBy

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, IndexModel, IndexOptions, Updates}
import org.mongodb.scala.model.Updates.combine
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.personaldetailsvalidation.AssociationRepositoryConstants._
import uk.gov.hmrc.personaldetailsvalidation.formats.AssociationFormat.associationFormat
import uk.gov.hmrc.personaldetailsvalidation.model.Association

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssociationMongoRepository])
trait AssociationRepository {
  def insertRecord(association: Association): Future[Unit]
  def getRecord(credentialId: String, sessionId: String): Future[Option[Association]]
  def drop: Future[Unit]
}

@Singleton
class AssociationMongoRepository @Inject() (config: PersonalDetailsValidationMongoRepositoryConfig,
                                            mongoComponent: MongoComponent)(implicit executionContext: ExecutionContext)
  extends PlayMongoRepository[Association](
    collectionName = association,
    mongoComponent = mongoComponent,
    domainFormat = associationFormat,
    indexes = Seq(
      IndexModel(
        ascending(lastUpdated),
        indexOptions = IndexOptions().name("expireAfterSeconds").expireAfter(config.collectionTtl.getSeconds, TimeUnit.SECONDS)
      ),
      IndexModel(
        ascending(credentialId, sessionId),
        indexOptions = IndexOptions().name("credentialId_sessionId_index").unique(true)
      )
    )
  ) with AssociationRepository with Logging
{

  def insertRecord(association: Association): Future[Unit] = {

    collection.findOneAndUpdate(
      filter(association.credentialId, association.sessionId),
      combine(
        Updates.set(credentialId, association.credentialId),
        Updates.set(sessionId, association.sessionId),
        Updates.set(validationId, association.validationId),
        Updates.set(lastUpdated, association.lastUpdated)
      ),
      FindOneAndUpdateOptions().upsert(true)
    ).toFuture()
      .map(_ => ()).recover {
      case error =>
        logger.error(s"Attempt to update association document failed for validation identifier $validationId")
        throw error
    }

  }

  def getRecord(credentialId: String, sessionId: String): Future[Option[Association]] = {

    collection.find(
      filter(credentialId, sessionId)
    ).headOption()
      .map {
        case Some(association) => Some(association)
        case None =>
          logger.warn(s"No association record found for session id $sessionId")
          None
      }.recover {
      case error =>
        logger.error(s"Attempt to retrieve association document failed for session identifier $sessionId")
        throw error
    }

  }

  def drop: Future[Unit] = collection.drop().toFuture.map(_ => ())

  private def filter(associationCredentialId: String, associationSessionId: String): Bson =
    Filters.and(
      Filters.equal(credentialId, associationCredentialId),
      Filters.equal(sessionId, associationSessionId)
    )

}

object AssociationRepositoryConstants {

  val association: String = "association"
  val credentialId: String = "credentialId"
  val sessionId : String = "sessionId"
  val validationId: String = "validationId"
  val lastUpdated: String = "lastUpdated"

}
