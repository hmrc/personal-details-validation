/*
 * Copyright 2025 HM Revenue & Customs
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
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.{Filters, IndexModel}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.personaldetailsvalidation.model.ValidationId
import uk.gov.hmrc.uuid.UUIDProvider

import java.time.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.SECONDS

class PersonalDetailsValidationMongoRepositorySpec
  extends UnitSpec
    with MongoSupport
    with CommonTestData
    with ScalaFutures
    with IntegrationPatience {

  private trait Setup {
    await(repository.collection.drop().toFuture())
  }

  implicit val ttlSeconds: Long = 100

  val config: PersonalDetailsValidationMongoRepositoryConfig = new PersonalDetailsValidationMongoRepositoryConfig(mock[Configuration]) {
    override lazy val collectionTtl: Duration = Duration.ofSeconds(ttlSeconds)
  }

  protected def repository = new PersonalDetailsValidationRepository(config, mongoComponent)

  "create" should {
    Set(
      personalDetailsValidationSuccess,
      personalDetailsValidationFailure
    ) foreach { personalDetailsValidation =>
      s"be able to insert ${personalDetailsValidation.getClass.getSimpleName}" in {
        repository.create(personalDetailsValidation).value.futureValue shouldBe Right(Done)
        repository.get(personalDetailsValidation.id).futureValue shouldBe Some(personalDetailsValidation)
      }
    }

    "convert exception into Either.Left" in new Setup {
      repository.create(personalDetailsValidationSuccess).value.futureValue shouldBe Right(Done)
    }

    "add 'createdAt' field with current time when persisting the document" in new Setup {

      val validationId: String = personalDetailsValidationSuccess.id.value.toString

      repository.create(personalDetailsValidationSuccess).value.futureValue shouldBe Right(Done)

      repository.collection.find(filter = Filters.eq("id", validationId)).toFuture().map {
        _.size
      }.futureValue shouldBe 1
    }
  }

  "get" should {

    "return None if document not found" in new Setup {
      implicit val uuidProvider: UUIDProvider = new UUIDProvider()
      repository.get(ValidationId()).futureValue shouldBe None
    }

    "return Document if document found in collection" in new Setup {
      repository.create(personalDetailsValidationSuccess).value.futureValue shouldBe Right(Done)
      repository.get(personalDetailsValidationSuccess.id).futureValue shouldBe Some(personalDetailsValidationSuccess)
    }

  }

  "repository" should {

    "create ttl on collection" in new Setup {
      val indexes: Seq[IndexModel] = repository
        .indexes
        .filter(_.getOptions.getName == "expireAfterSeconds")

      indexes.size shouldBe 1
      indexes.head.getKeys.toBsonDocument shouldBe BsonDocument("createdAt" -> 1)
      indexes.head.getOptions.getExpireAfter(SECONDS) shouldBe 100
    }

  }

}
