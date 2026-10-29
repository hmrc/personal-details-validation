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

package uk.gov.hmrc.personaldetailsvalidation.controllers.test

import org.apache.pekko.Done
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{CREATED, INTERNAL_SERVER_ERROR, NO_CONTENT}
import play.api.libs.json.Json
import play.api.test.Helpers.stubControllerComponents
import support.UnitSpec
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetailsValidationRetryRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestCredIdRetryControllerSpec extends UnitSpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  val mockRetryRepository: PersonalDetailsValidationRetryRepository = mock[PersonalDetailsValidationRetryRepository]

  val controller = new TestCredIdRetryController(mockRetryRepository, stubControllerComponents())

  override def beforeEach(): Unit = {
    reset(mockRetryRepository)
    super.beforeEach()
  }

  "setCredIdAttempts" should {

    "upsert the attempts for the credId" in {
      when(mockRetryRepository.upsertAttempts(any[String], any[Int])).thenReturn(Future.successful(Right(Done)))

      val response = controller
        .setCredIdAttempts(request.withBody(Json.obj("credId" -> "cred-id-1", "attempts" -> 2)))
        .futureValue

      status(response) shouldBe CREATED

      val credIdCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val attemptsCaptor: ArgumentCaptor[Int] = ArgumentCaptor.forClass(classOf[Int])
      verify(mockRetryRepository).upsertAttempts(credIdCaptor.capture(), attemptsCaptor.capture())
      credIdCaptor.getValue shouldBe "cred-id-1"
      attemptsCaptor.getValue shouldBe 2
    }

    "return internal server error when the store write fails" in {
      when(mockRetryRepository.upsertAttempts(any[String], any[Int]))
        .thenReturn(Future.successful(Left(new RuntimeException("mongo down"))))

      val response = controller
        .setCredIdAttempts(request.withBody(Json.obj("credId" -> "cred-id-1", "attempts" -> 3)))
        .futureValue

      status(response) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "deleteCredIdAttempts" should {

    "delete the attempts for the credId" in {
      when(mockRetryRepository.deleteAttempts(any[String])).thenReturn(Future.successful(Right(Done)))

      val response = controller
        .deleteCredIdAttempts(request.withBody(Json.obj("credId" -> "cred-id-1")))
        .futureValue

      status(response) shouldBe NO_CONTENT

      val credIdCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(mockRetryRepository).deleteAttempts(credIdCaptor.capture())
      credIdCaptor.getValue shouldBe "cred-id-1"
    }

    "return internal server error when the delete fails" in {
      when(mockRetryRepository.deleteAttempts(any[String]))
        .thenReturn(Future.successful(Left(new RuntimeException("mongo down"))))

      val response = controller
        .deleteCredIdAttempts(request.withBody(Json.obj("credId" -> "cred-id-1")))
        .futureValue

      status(response) shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
