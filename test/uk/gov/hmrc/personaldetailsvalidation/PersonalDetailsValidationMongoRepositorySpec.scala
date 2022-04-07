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
import generators.Generators.Implicits._
import generators.ObjectGenerators._
import mongo.MongoIndexVerifier
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Configuration
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadConcern
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Descending
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import support.UnitSpec
import uk.gov.hmrc.datetime.CurrentTimeProvider
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.mongoEntity
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.personaldetailsvalidation.formats.PersonalDetailsValidationFormat.personalDetailsValidationFormats
import uk.gov.hmrc.personaldetailsvalidation.model.{SuccessfulPersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.uuid.UUIDProvider

import java.time.ZoneOffset.UTC
import java.time.{Duration, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

class PersonalDetailsValidationMongoRepositorySpec
  extends UnitSpec
    with MongoSpecSupport
    with MongoIndexVerifier
    with MockFactory
    with ScalaFutures
    with IntegrationPatience {

  "create" should {
    Set(
      successfulPersonalDetailsValidationObjects.generateOne,
      failedPersonalDetailsValidationObjects.generateOne
    ) foreach { personalDetailsValidation =>
      s"be able to insert ${personalDetailsValidation.getClass.getSimpleName}" in new Setup {
        repository.create(personalDetailsValidation).value.futureValue shouldBe Right(Done)
        repository.get(personalDetailsValidation.id).futureValue shouldBe Some(personalDetailsValidation)
      }
    }

    "convert exception into Either.Left" in new Setup {
      val personalDetailsValidation: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
      repository.create(personalDetailsValidation).value.futureValue shouldBe Right(Done)
      repository.create(personalDetailsValidation).value.futureValue shouldBe a[Left[_, _]]
    }

    "add 'createdAt' field with current time when persisting the document" in new Setup {
      val personalDetailsValidation: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
      val validationId: String = personalDetailsValidation.id.value.toString

      repository.create(personalDetailsValidation).value.futureValue shouldBe Right(Done)

      bsonCollection(repository.collection.name)().count(
        selector = Some(BSONDocument("_id" -> validationId, "createdAt" -> BSONDateTime(currentTime.atZone(UTC).toInstant.toEpochMilli))),
        limit = None, skip = 0, hint = None, readConcern = ReadConcern.Local
      ).futureValue shouldBe 1
    }
  }

  "get" should {

    "return None if document not found in either new or old collection" in new Setup {
      repository.get(ValidationId()).futureValue shouldBe None
    }

    "return Document if document found in new collection" in new Setup {

      val pdvDoc: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
      repository.create(pdvDoc).value.futureValue shouldBe Right(Done)
      repository.get(pdvDoc.id).futureValue shouldBe Some(pdvDoc)
    }

    "return Document if document not in new collection, but found in old collection" in new Setup {

      // manually insert a doc into the old repo (create method here is banned)
      private val pdvDoc = successfulPersonalDetailsValidationObjects.generateOne

      private val document: JsObject =
        mongoEntity(personalDetailsValidationFormats).writes(pdvDoc).as[JsObject]

      import reactivemongo.play.json.ImplicitBSONHandlers._
      await(pdvOldRepository.collection.insert(ordered = false).one(document))

      // now check we can access via fallback:
      
      repository.get(pdvDoc.id).futureValue shouldBe Some(pdvDoc)
    }

  }

  "repository" should {

    "create ttl on collection" in new Setup {
      val expectedIndex: Index =
        Index(Seq("createdAt" -> Descending),
          name = Some("personal-details-validation-ttl-index"),
          options = BSONDocument("expireAfterSeconds" -> ttlSeconds))
      Thread.sleep(500) // wait for index to be created
      verify(expectedIndex).on(repository.collection.name)
    }

  }

  private trait Setup {

    implicit val uuidProvider: UUIDProvider = new UUIDProvider()
    implicit val ttlSeconds: Long = 100
    await(mongo().drop())

    implicit val currentTimeProvider: CurrentTimeProvider = stub[CurrentTimeProvider]

    val config: PersonalDetailsValidationMongoRepositoryConfig = new PersonalDetailsValidationMongoRepositoryConfig(mock[Configuration]) {
      override lazy val collectionTtl: Duration = Duration.ofSeconds(ttlSeconds)
    }

    val pdvOldRepository: PdvOldRepository = new PdvOldRepository(new ReactiveMongoComponent {
      override val mongoConnector: MongoConnector = mongoConnectorForTest
    })

    val currentTime: LocalDateTime = LocalDateTime.now()

    currentTimeProvider.apply _ when() returns currentTime

    val repository = new PersonalDetailsValidationRepository(config, new ReactiveMongoComponent {
      override val mongoConnector: MongoConnector = mongoConnectorForTest
    }, pdvOldRepository)

  }

}
