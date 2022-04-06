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
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import support.UnitSpec
import uk.gov.hmrc.datetime.CurrentTimeProvider
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidationWithCreateTimeStamp, SuccessfulPersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.uuid.UUIDProvider

import java.time.{Duration, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.SECONDS

class PersonalDetailsValidationMongoRepositorySpec
  extends UnitSpec
    with GuiceOneAppPerSuite
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
        repository.get(personalDetailsValidation.id).futureValue.get.personalDetailsValidation shouldBe personalDetailsValidation
      }
    }

    "convert exception into Either.Left" in new Setup {
      val personalDetailsValidation: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
      repository.create(personalDetailsValidation).value.futureValue shouldBe Right(Done)
    }

    "add 'createdAt' field with current time when persisting the document" in new Setup {
      val personalDetailsValidation: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
      val validationId: String = personalDetailsValidation.id.value.toString

      repository.create(personalDetailsValidation).value.futureValue shouldBe Right(Done)

      repository.collection.find(filter = Filters.eq("personalDetailsValidation.id",validationId)).toFuture().map{
        _.map(_.createdAt).size
      }.futureValue shouldBe 1
    }
  }

  "get" should {

    "return None if document not found in either new or old collection" in new Setup {
      repository.get(ValidationId()).futureValue shouldBe None
    }

    "return Document if document found in new collection" in new Setup {

      val pdvDoc: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
      repository.create(pdvDoc).value.futureValue shouldBe Right(Done)
      repository.get(pdvDoc.id).futureValue.get.personalDetailsValidation shouldBe pdvDoc
    }

    "return Document if document not in new collection, but found in old collection" in new Setup {

      // manually insert a doc into the old repo (create method here is banned)
      private val pdvDoc: SuccessfulPersonalDetailsValidation = successfulPersonalDetailsValidationObjects.generateOne
      private val pdvWithTimeStampDoc: PersonalDetailsValidationWithCreateTimeStamp = PersonalDetailsValidationWithCreateTimeStamp(pdvDoc, LocalDateTime.now())

      await(pdvOldRepository.collection.insertOne(pdvWithTimeStampDoc).toFuture())

      // now check we can access via fallback:
      repository.get(pdvDoc.id).futureValue.get.personalDetailsValidation shouldBe pdvDoc
    }

  }

  "repository" should {

    "create ttl on collection" in new Setup {
      val indexes: Seq[IndexModel] = repository.indexes

      indexes.size shouldBe 1
      indexes.head.getKeys.toBsonDocument shouldBe BsonDocument("createdAt" -> 1)
      indexes.head.getOptions.getExpireAfter(SECONDS) shouldBe 100
    }

  }

  trait Setup {

    implicit val uuidProvider: UUIDProvider = new UUIDProvider()
    implicit val ttlSeconds: Long = 100
    val mongoComponent: MongoComponent = app.injector.instanceOf[MongoComponent]
    val pdvOldRepository: PdvOldRepository = new PdvOldRepository(mongoComponent)

    await(pdvOldRepository.collection.drop().toFuture())

    implicit val currentTimeProvider: CurrentTimeProvider = stub[CurrentTimeProvider]

    val config: PersonalDetailsValidationMongoRepositoryConfig = new PersonalDetailsValidationMongoRepositoryConfig(mock[Configuration]) {
      override lazy val collectionTtl: Duration = Duration.ofSeconds(ttlSeconds)
    }


    val currentTime: LocalDateTime = LocalDateTime.now()

    currentTimeProvider.apply _ when() returns currentTime

    val repository = new PersonalDetailsValidationRepository(config, mongoComponent, pdvOldRepository)
    await(repository.collection.drop().toFuture())
  }

}
