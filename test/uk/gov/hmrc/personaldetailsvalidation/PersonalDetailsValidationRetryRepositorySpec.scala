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

import cats.data.EitherT
import com.mongodb.MongoException
import com.mongodb.client.result.{DeleteResult, UpdateResult}
import org.apache.pekko.Done
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.mongodb.scala.*
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.ReplaceOptions
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import support.{CommonTestData, UnitSpec}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

import java.time.Duration
import java.util.concurrent.TimeUnit

class TestPersonalDetailsValidationMongoRepositoryConfig(configuration: Configuration) extends PersonalDetailsValidationMongoRepositoryConfig(configuration) {
  override lazy val collectionTtl: Duration = Duration.ofSeconds(1)
}

class PersonalDetailsValidationRetryRepositorySpec extends UnitSpec with CommonTestData with MockitoSugar with ScalaFutures {

  "PersonalDetailsValidationRetryRepository" should {

    "successfully record a validation attempt" in new Setup {

      val mockUpdateResult: UpdateResult = mock[UpdateResult]

      when(mockCollection.replaceOne(any[Bson], any[Retry], any[ReplaceOptions])).thenReturn(SingleObservable(mockUpdateResult))

      val result: Either[Exception, Done] = repository.recordAttempt(testCredId).futureValue

      result match {
        case Right(done) => done shouldBe Done
        case Left(exception) => fail(s"Expected Right(Done) but got Left($exception)")
      }

    }

    "raise an exception when mongo fails when recording a validation attempt" in new Setup {

      val failedObservable: SingleObservable[UpdateResult] = mock[SingleObservable[UpdateResult]]
      when(failedObservable.toFuture()).thenReturn(Future.failed(new MongoException(exceptionMessage)))

      when(mockCollection.replaceOne(any[Bson], any[Retry], any[ReplaceOptions])).thenReturn(failedObservable)

      val result: Either[Exception, Done] = repository.recordAttempt(testCredId).futureValue

      result match {
        case Right(_) => fail("Expected Left(Exception) but got Right(Done)")
        case Left(exception) => exception shouldBe a[MongoException]
          exception.getMessage shouldBe exceptionMessage
      }

    }

    "successfully delete validation attempts" in new Setup {

      val mockDeleteResult: DeleteResult = mock[DeleteResult]
      when(mockCollection.deleteOne(any[Bson])).thenReturn(SingleObservable(mockDeleteResult))

      val result: Either[Exception, Done] = repository.deleteAttempts(testCredId).futureValue

      result match {
        case Right(done) => done shouldBe Done
        case Left(exception) => fail(s"Expected Right(Done) but got Left($exception)")
      }

    }

    "raise an exception when mongo fails when deleting validation attempts" in new Setup {

      val failedObservable: SingleObservable[DeleteResult] = mock[SingleObservable[DeleteResult]]
      when(failedObservable.toFuture()).thenReturn(Future.failed(new MongoException(exceptionMessage)))

      when(mockCollection.deleteOne(any[Bson])).thenReturn(failedObservable)

      val result: Either[Exception, Done] = repository.deleteAttempts(testCredId).futureValue

      result match {
        case Right(_) => fail("Expected Left(Exception) but got Right(Done)")
        case Left(exception) => exception shouldBe a[MongoException]
          exception.getMessage shouldBe exceptionMessage
      }

    }

    "return zero for the number of retry attempts when no validation attempts have been made" in new Setup {

     val mockFindObservable: FindObservable[Retry] = mock[FindObservable[Retry]]
     val mockSingleObservable: SingleObservable[Seq[Retry]] = mock[SingleObservable[Seq[Retry]]]

      when(mockCollection.find[Retry](any[Bson])(using any, any)).thenReturn(mockFindObservable)
      when(mockFindObservable.collect()).thenReturn(mockSingleObservable)
      when(mockSingleObservable.toFuture()).thenReturn(Future.successful(Seq.empty))

      val result: EitherT[Future, Exception, Int] = repository.getAttemptsT(Some(testCredId))(using ExecutionContext.global)

      result.value.futureValue match {
        case Right(attempts) => attempts shouldBe 0
        case Left(exception) => fail(s"Expected Right(0) but got Left($exception)")
      }

    }

    "return the number of retry attempts when validation attempts have been made" in new Setup {

      val mockFindObservable: FindObservable[Retry] = mock[FindObservable[Retry]]
      val mockSingleObservable: SingleObservable[Seq[Retry]] = mock[SingleObservable[Seq[Retry]]]

      when(mockCollection.find[Retry](any[Bson])(using any, any)).thenReturn(mockFindObservable)
      when(mockFindObservable.collect()).thenReturn(mockSingleObservable)
      when(mockSingleObservable.toFuture()).thenReturn(Future.successful(Seq(Retry(testCredId, Some(2)))))

      val result: EitherT[Future, Exception, Int] = repository.getAttemptsT(Some(testCredId))(using ExecutionContext.global)

      result.value.futureValue match {
        case Right(attempts) => attempts shouldBe 2
        case Left(exception) => fail(s"Expected Right(2) but got Left($exception)")
      }

    }

    "return zero for the number of retry attempts when no credId is provided" in new Setup {

      val result: EitherT[Future, Exception, Int] = repository.getAttemptsT(None)(using ExecutionContext.global)

      result.value.futureValue match {
        case Right(attempts) => attempts shouldBe 0
        case Left(exception) => fail(s"Expected Right(0) but got Left($exception)")
      }

    }

    "raise an exception when mongo fails when retrieving the number of validation attempts" in new Setup {

      val mockFindObservable: FindObservable[Retry] = mock[FindObservable[Retry]]
      val mockSingleObservable: SingleObservable[Seq[Retry]] = mock[SingleObservable[Seq[Retry]]]

      when(mockCollection.find[Retry](any[Bson])(using any, any)).thenReturn(mockFindObservable)
      when(mockFindObservable.collect()).thenReturn(mockSingleObservable)
      when(mockSingleObservable.toFuture()).thenReturn(Future.failed(new MongoException(exceptionMessage)))

      val result: EitherT[Future, Exception, Int] = repository.getAttemptsT(Some(testCredId))(using ExecutionContext.global)

      result.value.futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(attempts)")
        case Left(exception) => exception shouldBe a[MongoException]
          exception.getMessage shouldBe exceptionMessage
      }

    }

    "return Done when updating the number of retry attempts after a successful validation attempt" in new Setup {

      val mockDeleteResult: DeleteResult = mock[DeleteResult]
      when(mockCollection.deleteOne(any[Bson])).thenReturn(SingleObservable(mockDeleteResult))

      val result: EitherT[Future, Exception, Done] = repository.updateRetryAttemptsT(Some(testCredId), personalDetailsValidationSuccess)(using ExecutionContext.global)

      result.value.futureValue match {
        case Right(done) => done shouldBe Done
        case Left(exception) => fail(s"Expected Right(Done) but got Left($exception)")
      }

    }

    "return Done when updating the number of retry attempts after a failed validation attempt" in new Setup {

      val mockFindObservable: FindObservable[Retry] = mock[FindObservable[Retry]]
      val mockSingleObservable: SingleObservable[Seq[Retry]] = mock[SingleObservable[Seq[Retry]]]

      val mockUpdateResult: UpdateResult = mock[UpdateResult]

      when(mockCollection.find[Retry](any[Bson])(using any, any)).thenReturn(mockFindObservable)
      when(mockFindObservable.collect()).thenReturn(mockSingleObservable)
      when(mockSingleObservable.toFuture()).thenReturn(Future.successful(Seq(Retry(testCredId, Some(1)))))

      val retryCaptor: ArgumentCaptor[Retry] = ArgumentCaptor.forClass(classOf[Retry])

      when(mockCollection.replaceOne(any[Bson], any[Retry], any[ReplaceOptions])).thenReturn(SingleObservable(mockUpdateResult))

      val result: EitherT[Future, Exception, Done] = repository.updateRetryAttemptsT(Some(testCredId), personalDetailsValidationFailure)(using ExecutionContext.global)

      result.value.futureValue match {
        case Right(done) => done shouldBe Done
        case Left(exception) => fail(s"Expected Right(Done) but got Left($exception)")
      }

      verify(mockCollection, times(1)).replaceOne(any[Bson], retryCaptor.capture(), any[ReplaceOptions])

      retryCaptor.getValue.attempts shouldBe Some(2)
    }

    "raise an exception when getAttempts fails when updating the number of retry attempts after a failed validation attempt" in new Setup {

      val mockFindObservable: FindObservable[Retry] = mock[FindObservable[Retry]]
      val mockSingleObservable: SingleObservable[Seq[Retry]] = mock[SingleObservable[Seq[Retry]]]

      when(mockCollection.find[Retry](any[Bson])(using any, any)).thenReturn(mockFindObservable)
      when(mockFindObservable.collect()).thenReturn(mockSingleObservable)
      when(mockSingleObservable.toFuture()).thenReturn(Future.failed(new MongoException(exceptionMessage)))

      val result: EitherT[Future, Exception, Done] = repository.updateRetryAttemptsT(Some(testCredId), personalDetailsValidationFailure)(using ExecutionContext.global)

      result.value.futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(Done)")
        case Left(exception) => exception shouldBe a[MongoException]
          exception.getMessage shouldBe exceptionMessage
      }

    }

    "raise an exception when recordAttempt fails when updating the number of retry attempts after a failed validation attempt" in new Setup {

      val mockFindObservable: FindObservable[Retry] = mock[FindObservable[Retry]]
      val mockSingleObservable: SingleObservable[Seq[Retry]] = mock[SingleObservable[Seq[Retry]]]

      when(mockCollection.find[Retry](any[Bson])(using any, any)).thenReturn(mockFindObservable)
      when(mockFindObservable.collect()).thenReturn(mockSingleObservable)
      when(mockSingleObservable.toFuture()).thenReturn(Future.successful(Seq(Retry(testCredId, Some(1)))))

      val failedObservable: SingleObservable[UpdateResult] = mock[SingleObservable[UpdateResult]]
      when(failedObservable.toFuture()).thenReturn(Future.failed(new MongoException(exceptionMessage)))

      when(mockCollection.replaceOne(any[Bson], any[Retry], any[ReplaceOptions])).thenReturn(failedObservable)

      val result: EitherT[Future, Exception, Done] = repository.updateRetryAttemptsT(Some(testCredId), personalDetailsValidationFailure)(using ExecutionContext.global)

      result.value.futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(Done)")
        case Left(exception) => exception shouldBe a[MongoException]
          exception.getMessage shouldBe exceptionMessage
      }

    }

  }

  trait Setup {

    /**
     * Note this test mocks the mongo component and collection used by PlayMongoRepository. This is sufficient to test how
     * the repository handles successful and failed calls to the mongo collection. However, it is dependent on there being
     * no changes to PlayMongoRepository.
     *
     * A simpler, though less efficient approach, is to extend GuiceOneAppPerSuite and use an injector to create the mongo component
     * and then override the val collection and method ensureIndexes.
     *
     */

    val mockConfiguration: Configuration = mock[Configuration]

    val pdvConfig: PersonalDetailsValidationMongoRepositoryConfig = new TestPersonalDetailsValidationMongoRepositoryConfig(mockConfiguration)

    val mockMongoComponent: MongoComponent = mock[MongoComponent]
    val mockCollection: MongoCollection[Retry] = mock[MongoCollection[Retry]]

    when(mockMongoComponent.initTimeout).thenReturn(FiniteDuration(1L, TimeUnit.SECONDS))


    val repository: PersonalDetailsValidationRetryRepository = {
      new PersonalDetailsValidationRetryRepository(pdvConfig, mockMongoComponent)(using ExecutionContext.global) {

        override lazy val collection: MongoCollection[Retry] = mockCollection

        override def ensureIndexes(): Future[Seq[String]] = Future.successful(Seq.empty)
      }

    }

    val exceptionMessage: String = "MongoDB error"

  }

}