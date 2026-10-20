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
import org.mockito.Mockito.{reset, verify, verifyNoInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers.stubControllerComponents
import play.api.http.Status.{BAD_REQUEST, CREATED, INTERNAL_SERVER_ERROR, NO_CONTENT}
import support.UnitSpec
import uk.gov.hmrc.personaldetailsvalidation.{NinoLockConfig, NinoLockRepository}
import uk.gov.hmrc.personaldetailsvalidation.model.NinoLock
import uk.gov.hmrc.personaldetailsvalidation.services.NinoKeyService

import java.time.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestNinoLockControllerSpec extends UnitSpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  val mockNinoLockRepository: NinoLockRepository = mock[NinoLockRepository]

  val ninoLockConfig: NinoLockConfig = new NinoLockConfig(mock[Configuration]) {
    override lazy val maxFailedAttempts: Int = 3
    override lazy val lockDuration: Duration = Duration.ofHours(24)
  }

  val controller = new TestNinoLockController(new NinoKeyService, mockNinoLockRepository, ninoLockConfig, stubControllerComponents())

  override def beforeEach(): Unit = {
    reset(mockNinoLockRepository)
    super.beforeEach()
  }

  "upsertNinoLock" should {

    "create a locked record for the normalised nino" in {
      when(mockNinoLockRepository.upsert(any[NinoLock])).thenReturn(Future.successful(Right(Done)))

      val response = controller
        .upsertNinoLock(request.withBody(Json.obj("nino" -> "aa 00 00 03 d", "failedAttempts" -> 3, "locked" -> true)))
        .futureValue

      status(response) shouldBe CREATED

      val captor: ArgumentCaptor[NinoLock] = ArgumentCaptor.forClass(classOf[NinoLock])
      verify(mockNinoLockRepository).upsert(captor.capture())
      captor.getValue.ninoKey shouldBe "AA000003"
      captor.getValue.failedAttempts shouldBe 3
      captor.getValue.lockedUntil shouldBe defined
    }

    "create an unlocked record when locked is not set" in {
      when(mockNinoLockRepository.upsert(any[NinoLock])).thenReturn(Future.successful(Right(Done)))

      val response = controller
        .upsertNinoLock(request.withBody(Json.obj("nino" -> "AA000003A", "failedAttempts" -> 2)))
        .futureValue

      status(response) shouldBe CREATED

      val captor: ArgumentCaptor[NinoLock] = ArgumentCaptor.forClass(classOf[NinoLock])
      verify(mockNinoLockRepository).upsert(captor.capture())
      captor.getValue.ninoKey shouldBe "AA000003"
      captor.getValue.lockedUntil shouldBe None
    }

    "return bad request when the nino is invalid" in {
      val response = controller.upsertNinoLock(request.withBody(Json.obj("nino" -> "not-a-nino"))).futureValue

      status(response) shouldBe BAD_REQUEST
      verifyNoInteractions(mockNinoLockRepository)
    }

    "return internal server error when the store write fails" in {
      when(mockNinoLockRepository.upsert(any[NinoLock]))
        .thenReturn(Future.successful(Left(new RuntimeException("mongo down"))))

      val response = controller.upsertNinoLock(request.withBody(Json.obj("nino" -> "AA000003D"))).futureValue

      status(response) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "deleteNinoLock" should {

    "delete the record for the normalised nino" in {
      when(mockNinoLockRepository.delete(any[String])).thenReturn(Future.successful(Right(Done)))

      val response = controller.deleteNinoLock(request.withBody(Json.obj("nino" -> "AA000003D"))).futureValue

      status(response) shouldBe NO_CONTENT

      val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(mockNinoLockRepository).delete(captor.capture())
      captor.getValue shouldBe "AA000003"
    }

    "return internal server error when the delete fails" in {
      when(mockNinoLockRepository.delete(any[String]))
        .thenReturn(Future.successful(Left(new RuntimeException("mongo down"))))

      val response = controller.deleteNinoLock(request.withBody(Json.obj("nino" -> "AA000003D"))).futureValue

      status(response) shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
