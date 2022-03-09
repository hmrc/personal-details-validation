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

import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

trait TtlIndexedReactiveRepository[A, B] { self: ReactiveRepository[A, B] =>

  val ttlIndex = "personal-details-validation-ttl-index"
  val OptExpireAfterSeconds = "expireAfterSeconds"
  val createdAtField = "createdAt"

  val ttl: Long

  def maybeCreateTtlIndex(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {

    import reactivemongo.bson.DefaultBSONHandlers._

    val indexes: Future[List[Index]] = collection.indexesManager.list()

    def ensureTtlIndex(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
      Future.sequence(Seq(collection.indexesManager.ensure(
        Index(
          key = Seq(createdAtField -> IndexType.Descending),
          name = Some(ttlIndex),
          options = BSONDocument(OptExpireAfterSeconds -> ttl)
        )
      )))
    }

    def ttlHasChanged(index: Index): Boolean =
      !index.options.getAs[BSONLong](OptExpireAfterSeconds).contains(BSONLong(ttl))

    indexes.flatMap {
      idxs => {
        val maybeIndex = idxs.find(index => index.eventualName == ttlIndex && ttlHasChanged(index))

        maybeIndex.fold(ensureTtlIndex) { index =>
          collection.indexesManager.drop(index.eventualName).flatMap(_ => ensureTtlIndex)
        }
      }
    }
  }
}
