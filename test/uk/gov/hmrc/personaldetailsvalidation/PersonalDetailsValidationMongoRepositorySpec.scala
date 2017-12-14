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

import generators.Generators.Implicits._
import generators.ObjectGenerators._
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Descending
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.personaldetailsvalidation.model.ValidationId
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.ExecutionContext.Implicits.global

class PersonalDetailsValidationMongoRepositorySpec
  extends UnitSpec
    with MongoSpecSupport
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
  }

  "get" should {
    "return None if document not found" in new Setup {
      repository.get(ValidationId()).futureValue shouldBe None
    }
  }

  "repository" should {
    "create ttl on collection" in new Setup {
      val expectedIndex = Index(Seq("createdAt" -> Descending), name = Some("personal-details-validation-ttl-index"), options = BSONDocument("expireAfterSeconds" -> ttlSeconds))
      verifyIndex(expectedIndex)
    }
  }

  private trait Setup {
    implicit val uuidProvider: UUIDProvider = new UUIDProvider()
    implicit val ttlSeconds = 100
    await(mongo().drop())
    val repository = new PersonalDetailsValidationMongoRepository(ttlSeconds: Int)(new ReactiveMongoComponent {
      override val mongoConnector = mongoConnectorForTest
    })

    def verifyIndex(expectedIndex: Index) = {
      val expectedIndexName = expectedIndex.eventualName
      val collectionIndexes = await(mongo().indexesManager.onCollection(repository.collection.name).list())

      val index = collectionIndexes
        .find(_.name.contains(expectedIndexName))
        .getOrElse(throw new RuntimeException(s"Index with name $expectedIndexName not found in collection ${repository.collection.name}"))

      //version of index is managed by mongodb. We don't want to assert on it.
      index shouldBe expectedIndex.copy(version = index.version)
    }
  }

}
