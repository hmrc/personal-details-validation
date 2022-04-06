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

///*
// * Copyright 2022 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package mongo
//
//import java.util.concurrent.TimeUnit
//
//import org.mongodb.scala.model.Indexes.ascending
//import org.mongodb.scala.model.{IndexModel, IndexOptions}
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.{BeforeAndAfterEach, Matchers}
//import org.scalatestplus.play.guice.GuiceOneAppPerSuite
//import support.UnitSpec
//import uk.gov.hmrc.mongo.MongoComponent
//import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
//import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetailsValidationWithCreateTimeStamp
//
//import scala.collection.Seq
//import scala.concurrent.ExecutionContext.Implicits.global
//
//trait MongoIndexVerifier extends Matchers with UnitSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {
//  def verify(expectedIndex: IndexModel) = new {
//    def on(collectionName: String) {
//      val mongoComponent: MongoComponent = app.injector.instanceOf[MongoComponent]
//      val service = new PlayMongoRepository(
//        mongoComponent,
//        "pdv-test",
//        PersonalDetailsValidationWithCreateTimeStamp.format,
//        indexes = Seq(
//          IndexModel(
//            ascending("createdAt"),
//            indexOptions = IndexOptions().name("expireAfterSeconds").expireAfter(120, TimeUnit.SECONDS)
//          ),
//          IndexModel(
//            ascending("journeyId"), IndexOptions().name("Primary").unique(true)
//          )
//        )
//      )
//      val expectedIndexName = expectedIndex.getKeys
//      val collectionIndexes = await(service.collection.drop().toFuture())
//
//      val index = collectionIndexes.
//        .find(_.name.contains(expectedIndexName))
//        .getOrElse(
//          throw new RuntimeException(s"Index with name $expectedIndexName not found in collection $collectionName; indexes found: $collectionIndexes")
//        )
//
//      //version of index is managed by mongodb. We don't want to assert on it.
//      index shouldBe expectedIndex.copy(version = index.version)
//    }
//  }
//
//}
