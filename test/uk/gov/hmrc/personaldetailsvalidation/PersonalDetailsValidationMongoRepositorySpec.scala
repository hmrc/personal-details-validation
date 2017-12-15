/*
 * Copyright 2017 HM Revenue & Customs
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

import java.time.ZoneOffset.UTC
import java.time.{Duration, LocalDateTime}

import generators.Generators.Implicits._
import generators.ObjectGenerators._
import mongo.MongoIndexVerifier
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Descending
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.datetime.CurrentTimeProvider
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.personaldetailsvalidation.model.ValidationId
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.ExecutionContext.Implicits.global

class PersonalDetailsValidationMongoRepositorySpec
  extends UnitSpec
    with MongoSpecSupport
    with MongoIndexVerifier
    with MockFactory
    with ScalaFutures {

  "create" should {
    Set(
      successfulPersonalDetailsValidationObjects.generateOne,
      failedPersonalDetailsValidationObjects.generateOne
    ) foreach { personalDetailsValidation =>

      s"be able to insert ${personalDetailsValidation.getClass.getSimpleName}" in new Setup {
        await(repository.create(personalDetailsValidation))

        repository.get(personalDetailsValidation.id).futureValue shouldBe Some(personalDetailsValidation)
      }
    }

    "add 'createdAt' field with current time when persisting the document" in new Setup {
      val personalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
      val validationId = personalDetailsValidation.id.value.toString

      await(repository.create(personalDetailsValidation))

      bsonCollection(repository.collection.name)().count(selector = Some(BSONDocument("_id" -> validationId, "createdAt" -> currentTime.atZone(UTC).toInstant.toEpochMilli))).futureValue shouldBe 1
    }
  }

  "get" should {
    "return None if document not found" in new Setup {
      repository.get(ValidationId()).futureValue shouldBe None
    }
  }

  "repository" should {
    "create ttl on collection" in new Setup {
      val expectedIndex = Index(Seq("createdAt" -> Descending), name = Some("personal-details-validation-ttl-index"), options = BSONDocument("expireAfterSeconds" -> ttlSeconds))
      verify(expectedIndex).on(repository.collection.name)
    }
  }

  private trait Setup {
    implicit val uuidProvider: UUIDProvider = new UUIDProvider()
    implicit val ttlSeconds: Long = 100
    await(mongo().drop())

    implicit val currentTimeProvider = stub[CurrentTimeProvider]

    val config = new PersonalDetailsValidationMongoRepositoryConfig()(mock[Configuration]) {
      override lazy val collectionTtl: Duration = Duration.ofSeconds(ttlSeconds)
    }

    val currentTime: LocalDateTime = LocalDateTime.now()

    currentTimeProvider.apply _ when() returns currentTime

    val repository = new PersonalDetailsValidationMongoRepository(config, new ReactiveMongoComponent {
      override val mongoConnector = mongoConnectorForTest
    })
  }
}