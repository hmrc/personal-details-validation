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

package mongo

import org.scalatest.Matchers
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global

trait MongoIndexVerifier extends Matchers {
  self: MongoSpecSupport with UnitSpec =>
  def verify(expectedIndex: Index) = new {
    def on(collectionName: String) {
      val expectedIndexName = expectedIndex.eventualName
      val collectionIndexes = await(mongo().indexesManager.onCollection(collectionName).list())

      val index = collectionIndexes
        .find(_.name.contains(expectedIndexName))
        .getOrElse(throw new RuntimeException(s"Index with name $expectedIndexName not found in collection $collectionName"))

      //version of index is managed by mongodb. We don't want to assert on it.
      index shouldBe expectedIndex.copy(version = index.version)
    }
  }

}
