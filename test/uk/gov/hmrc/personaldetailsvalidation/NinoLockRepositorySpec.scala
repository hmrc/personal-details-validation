/*
 * Copyright 2026 HM Revenue & Customs
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

import com.mongodb.ServerAddress
import com.mongodb.client.result.{DeleteResult, UpdateResult}
import com.mongodb.{MongoWriteException, WriteError}
import org.apache.pekko.Done
import org.bson.{BsonDocument, BsonNull}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mongodb.scala.*
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, ReplaceOptions, Updates}
import org.mongodb.scala.model.ReturnDocument.AFTER
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.personaldetailsvalidation.model.NinoLock

import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class NinoLockRepositorySpec extends UnitSpec with CommonTestData with MockitoSugar with ScalaFutures {

  "NinoLockRepository" should {

    val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)
    val expiresAt: LocalDateTime = now.plusHours(24)
    val ninoKey: String = "AA000003"

    "return the lock record when one exists for the ninoKey" in new Setup {
      val storedLock: NinoLock = ninoLock(failedAttempts = 1)
      when(mockFindObservable.headOption()).thenReturn(Future.successful(Some(storedLock)))

      repository.findByNinoKey(ninoKey).futureValue shouldBe Right(Some(storedLock))
    }

    "return Right(None) when no lock record exists for the ninoKey" in new Setup {
      when(mockFindObservable.headOption()).thenReturn(Future.successful(None))

      repository.findByNinoKey(ninoKey).futureValue shouldBe Right(None)
    }

    "raise an exception when mongo fails when finding a lock record" in new Setup {
      when(mockFindObservable.headOption()).thenReturn(Future.failed(new MongoException(exceptionMessage)))

      repository.findByNinoKey(ninoKey).futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(None)")
        case Left(ex) => ex shouldBe a[MongoException]
      }
    }

    "atomically increment the failed attempts and return the updated record" in new Setup {
      val updatedLock: NinoLock = ninoLock(failedAttempts = 2)
      stubFindOneAndUpdate(Future.successful(Some(updatedLock)))

      repository.incrementFailedAttempts(ninoKey, now, expiresAt).futureValue shouldBe Right(Some(updatedLock))

      val optionsCaptor: ArgumentCaptor[FindOneAndUpdateOptions] = ArgumentCaptor.forClass(classOf[FindOneAndUpdateOptions])
      val filterCaptor: ArgumentCaptor[Bson] = ArgumentCaptor.forClass(classOf[Bson])
      val updateCaptor: ArgumentCaptor[Bson] = ArgumentCaptor.forClass(classOf[Bson])
      verify(mockCollection, times(1)).findOneAndUpdate(filterCaptor.capture(), updateCaptor.capture(), optionsCaptor.capture())
      optionsCaptor.getValue.isUpsert shouldBe true
      optionsCaptor.getValue.getReturnDocument shouldBe AFTER
      filterCaptor.getValue.toBsonDocument shouldBe Filters.and(
        Filters.eq("ninoKey", ninoKey),
        Filters.or(Filters.exists("lockedUntil", false), Filters.eq("lockedUntil", BsonNull.VALUE))
      ).toBsonDocument
      updateCaptor.getValue.toBsonDocument shouldBe Updates.combine(
        Updates.inc("failedAttempts", 1),
        Updates.set("lastFailedAt", Date.from(now.toInstant(ZoneOffset.UTC))),
        Updates.set("expiresAt", Date.from(expiresAt.toInstant(ZoneOffset.UTC)))
      ).toBsonDocument
    }

    "raise an exception when mongo fails when incrementing the failed attempts" in new Setup {
      stubFindOneAndUpdate(Future.failed(new MongoException(exceptionMessage)))

      repository.incrementFailedAttempts(ninoKey, now, expiresAt).futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(lock)")
        case Left(ex) => ex shouldBe a[MongoException]
      }
      verify(mockCollection, times(1)).findOneAndUpdate(any[Bson], any[Bson], any[FindOneAndUpdateOptions])
    }

    "retry the increment once when an upsert duplicate key race occurs" in new Setup {
      val updatedLock: NinoLock = ninoLock(failedAttempts = 1)
      stubFindOneAndUpdate(Future.failed(duplicateKeyException), Future.successful(Some(updatedLock)))

      repository.incrementFailedAttempts(ninoKey, now, expiresAt).futureValue shouldBe Right(Some(updatedLock))
      verify(mockCollection, times(2)).findOneAndUpdate(any[Bson], any[Bson], any[FindOneAndUpdateOptions])
    }

    "not increment and return Right(None) when the record is already locked" in new Setup {
      stubFindOneAndUpdate(Future.failed(duplicateKeyException), Future.failed(duplicateKeyException))

      repository.incrementFailedAttempts(ninoKey, now, expiresAt).futureValue shouldBe Right(None)
      verify(mockCollection, times(2)).findOneAndUpdate(any[Bson], any[Bson], any[FindOneAndUpdateOptions])
    }

    "raise an exception when the increment returns no document" in new Setup {
      stubFindOneAndUpdate(Future.successful(None))

      repository.incrementFailedAttempts(ninoKey, now, expiresAt).futureValue match {
        case Left(ex) => ex.getMessage shouldBe "incrementFailedAttempts returned no document"
        case Right(_) => fail("Expected Left(Exception) but got Right")
      }
      verify(mockCollection, times(1)).findOneAndUpdate(any[Bson], any[Bson], any[FindOneAndUpdateOptions])
    }

    "raise an exception when the retry returns no document" in new Setup {
      stubFindOneAndUpdate(Future.failed(duplicateKeyException), Future.successful(None))

      repository.incrementFailedAttempts(ninoKey, now, expiresAt).futureValue match {
        case Left(ex) => ex.getMessage shouldBe "incrementFailedAttempts returned no document"
        case Right(_) => fail("Expected Left(Exception) but got Right")
      }
      verify(mockCollection, times(2)).findOneAndUpdate(any[Bson], any[Bson], any[FindOneAndUpdateOptions])
    }

    "return the error when the retry fails with a non-duplicate exception" in new Setup {
      val exception = new MongoException(exceptionMessage)
      stubFindOneAndUpdate(Future.failed(duplicateKeyException), Future.failed(exception))

      repository.incrementFailedAttempts(ninoKey, now, expiresAt).futureValue shouldBe Left(exception)
      verify(mockCollection, times(2)).findOneAndUpdate(any[Bson], any[Bson], any[FindOneAndUpdateOptions])
    }

    "retry duplicate key command exceptions" in new Setup {
      val updatedLock: NinoLock = ninoLock(failedAttempts = 1)
      val exception = new MongoCommandException(
        BsonDocument.parse("""{"ok": 0, "code": 11000, "errmsg": "E11000 duplicate key"}"""),
        new ServerAddress()
      )
      stubFindOneAndUpdate(Future.failed(exception), Future.successful(Some(updatedLock)))

      repository.incrementFailedAttempts(ninoKey, now, expiresAt).futureValue shouldBe Right(Some(updatedLock))
      verify(mockCollection, times(2)).findOneAndUpdate(any[Bson], any[Bson], any[FindOneAndUpdateOptions])
    }

    "return Right(None) after repeated duplicate key command exceptions" in new Setup {
      val exception = new MongoCommandException(
        BsonDocument.parse("""{"ok": 0, "code": 11000, "errmsg": "E11000 duplicate key"}"""),
        new ServerAddress()
      )
      stubFindOneAndUpdate(Future.failed(exception), Future.failed(exception))

      repository.incrementFailedAttempts(ninoKey, now, expiresAt).futureValue shouldBe Right(None)
      verify(mockCollection, times(2)).findOneAndUpdate(any[Bson], any[Bson], any[FindOneAndUpdateOptions])
    }

    "create the lock when no lock exists yet and return the updated record" in new Setup {
      val lockedRecord: NinoLock = ninoLock(failedAttempts = 3, lockedUntil = Some(expiresAt))
      stubFindOneAndUpdate(Future.successful(Some(lockedRecord)))

      repository.createLock(ninoKey, expiresAt).futureValue shouldBe Right(Some(lockedRecord))
    }

    "return Right(None) when a lock already exists (the lock must not be extended)" in new Setup {
      stubFindOneAndUpdate(Future.successful(None))

      repository.createLock(ninoKey, expiresAt).futureValue shouldBe Right(None)
    }

    "raise an exception when mongo fails when creating the lock" in new Setup {
      stubFindOneAndUpdate(Future.failed(new MongoException(exceptionMessage)))

      repository.createLock(ninoKey, expiresAt).futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(None)")
        case Left(ex) => ex shouldBe a[MongoException]
      }
    }

    "successfully delete a lock record" in new Setup {
      val mockDeleteResult: DeleteResult = mock[DeleteResult]
      when(mockCollection.deleteOne(any[Bson])).thenReturn(SingleObservable(mockDeleteResult))

      repository.delete(ninoKey).futureValue shouldBe Right(Done)
    }

    "raise an exception when mongo fails when deleting a lock record" in new Setup {
      val failedDeleteObservable: SingleObservable[DeleteResult] = mock[SingleObservable[DeleteResult]]
      when(failedDeleteObservable.toFuture()).thenReturn(Future.failed(new MongoException(exceptionMessage)))
      when(mockCollection.deleteOne(any[Bson])).thenReturn(failedDeleteObservable)

      repository.delete(ninoKey).futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(Done)")
        case Left(ex) => ex shouldBe a[MongoException]
      }
    }

    "successfully upsert a lock record" in new Setup {
      val mockUpdateResult: UpdateResult = mock[UpdateResult]
      when(mockCollection.replaceOne(any[Bson], any[NinoLock], any[ReplaceOptions])).thenReturn(SingleObservable(mockUpdateResult))

      repository.upsert(ninoLock(failedAttempts = 3, lockedUntil = Some(expiresAt))).futureValue shouldBe Right(Done)
    }

    "raise an exception when mongo fails when upserting a lock record" in new Setup {
      val failedReplaceObservable: SingleObservable[UpdateResult] = mock[SingleObservable[UpdateResult]]
      when(failedReplaceObservable.toFuture()).thenReturn(Future.failed(new MongoException(exceptionMessage)))
      when(mockCollection.replaceOne(any[Bson], any[NinoLock], any[ReplaceOptions])).thenReturn(failedReplaceObservable)

      repository.upsert(ninoLock(failedAttempts = 3, lockedUntil = Some(expiresAt))).futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(Done)")
        case Left(ex) => ex shouldBe a[MongoException]
      }
    }
  }

  "NinoLockRepository indexes" should {
    "include a unique index on ninoKey and a TTL index on expiresAt" in new Setup {
      val indexNames: Seq[String] = repository.indexes.map(_.getOptions.getName)
      indexNames should contain("ninoKeyUnique")
      indexNames should contain("expiresAtTtl")
    }
  }

  private def ninoLock(failedAttempts: Int, lockedUntil: Option[LocalDateTime] = None): NinoLock = {
    val now = LocalDateTime.of(2026, 1, 1, 12, 0)
    NinoLock(
      ninoKey = "AA000003",
      failedAttempts = failedAttempts,
      lastFailedAt = now,
      lockedUntil = lockedUntil,
      expiresAt = lockedUntil.getOrElse(now.plusHours(24))
    )
  }

  trait Setup {

    val exceptionMessage: String = "MongoDB error"

    val duplicateKeyException: MongoWriteException =
      new MongoWriteException(new WriteError(11000, "E11000 duplicate key", new BsonDocument()), new ServerAddress())

    val mockMongoComponent: MongoComponent = mock[MongoComponent]
    val mockCollection: MongoCollection[NinoLock] = mock[MongoCollection[NinoLock]]
    val mockFindObservable: FindObservable[NinoLock] = mock[FindObservable[NinoLock]]

    when(mockMongoComponent.initTimeout).thenReturn(FiniteDuration(1L, TimeUnit.SECONDS))
    when(mockCollection.find[NinoLock](any[Bson])(using any, any)).thenReturn(mockFindObservable)

    val repository: NinoLockRepository =
      new NinoLockRepository(mockMongoComponent)(using ExecutionContext.global) {
        override lazy val collection: MongoCollection[NinoLock] = mockCollection
        override def ensureIndexes(): Future[Seq[String]] = Future.successful(Seq.empty)
      }

    def stubFindOneAndUpdate(result: Future[Option[NinoLock]], subsequentResults: Future[Option[NinoLock]]*): Unit = {
      val mockObservable: SingleObservable[NinoLock] = mock[SingleObservable[NinoLock]]
      when(mockObservable.toFutureOption()).thenReturn(result, subsequentResults*)
      when(mockCollection.findOneAndUpdate(any[Bson], any[Bson], any[FindOneAndUpdateOptions])).thenReturn(mockObservable)
    }
  }
}
