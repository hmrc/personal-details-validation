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

import factory.ObjectFactory.randomPersonalDetailsValidation
import org.scalatest.concurrent.ScalaFutures
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class PersonalDetailsValidationMongoRepositorySpec extends UnitSpec with MongoSpecSupport with ScalaFutures {

  "create" should {
    "create the personal details validation document" in new Setup {
      val personalDetailsValidation = randomPersonalDetailsValidation
      await(repository.create(personalDetailsValidation))

      repository.get(personalDetailsValidation.id).futureValue should contain(personalDetailsValidation)
    }
  }

  "get" should {
    "return None if document not found" in new Setup {
      repository.get(ValidationId()).futureValue shouldBe None
    }
  }

  trait Setup {
    implicit val uuidProvider: UUIDProvider = new UUIDProvider()
    implicit val ec: ExecutionContextExecutor = ExecutionContext.Implicits.global
    val repository = new PersonalDetailsValidationMongoRepository(new ReactiveMongoComponent {
      override val mongoConnector = mongoConnectorForTest
    })
    await(repository.removeAll())
  }
}
