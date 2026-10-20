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

package uk.gov.hmrc.personaldetailsvalidation.services

import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify, verifyNoInteractions, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import support.{CommonTestData, UnitSpec}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.*
import uk.gov.hmrc.personaldetailsvalidation.model.*
import uk.gov.hmrc.personaldetailsvalidation.services.NinoRetryLockService.NinoLockStatus
import uk.gov.hmrc.personaldetailsvalidation.{NinoLockConfig, NinoLockRepository}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent

import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NinoRetryLockServiceSpec extends UnitSpec with CommonTestData with MockitoSugar with ScalaFutures {

  import NinoRetryLockServiceSpec.*

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
  override implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  "lockStatus" should {

    "return NoNinoJourney without touching the lock store when the personal details contain no nino" in new Setup {
      service.lockStatus(personalDetailsWithPostCode, testMaybeCredId).value.futureValue shouldBe Right(NinoLockStatus.NoNinoJourney)

      verifyNoInteractions(mockNinoLockRepository)
    }

    "return NotLocked when no lock record exists for the nino" in new Setup {
      when(mockNinoLockRepository.findByNinoKey(ninoKey)).thenReturn(Future.successful(Right(None)))

      service.lockStatus(personalDetailsWithNino, testMaybeCredId).value.futureValue shouldBe Right(NinoLockStatus.NotLocked)
    }

    "return Locked and send NinoSubmissionBlocked when the nino is actively locked" in new Setup {
      val lockedRecord: NinoLock = ninoRecord(lockedUntil = Some(LocalDateTime.now(ZoneOffset.UTC).plusHours(1)))
      when(mockNinoLockRepository.findByNinoKey(ninoKey)).thenReturn(Future.successful(Right(Some(lockedRecord))))

      service.lockStatus(personalDetailsWithNino, testMaybeCredId).value.futureValue match {
        case Right(NinoLockStatus.Locked(lockedUntilValue)) => lockedUntilValue shouldBe lockedRecord.lockedUntil.get
        case other                                          => fail(s"Expected Right(Locked) but got $other")
      }

      verify(mockAuditConnector).sendEvent(ninoSubmissionBlockedEvent)
    }

    "return NotLocked when the nino lock has expired" in new Setup {
      val expiredRecord: NinoLock = ninoRecord(
        lockedUntil = Some(LocalDateTime.now(ZoneOffset.UTC).minusHours(1)),
        lastFailedAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(25)
      )
      when(mockNinoLockRepository.findByNinoKey(ninoKey)).thenReturn(Future.successful(Right(Some(expiredRecord))))
      when(mockNinoLockRepository.delete(ninoKey)).thenReturn(Future.successful(Right(Done)))

      service.lockStatus(personalDetailsWithNino, testMaybeCredId).value.futureValue shouldBe Right(NinoLockStatus.NotLocked)

      verify(mockNinoLockRepository).delete(ninoKey)
    }

    "return NotLocked without deleting for a fresh below-threshold record" in new Setup {
      val activeRecord: NinoLock = ninoRecord(lastFailedAt = LocalDateTime.now(ZoneOffset.UTC))
      when(mockNinoLockRepository.findByNinoKey(ninoKey)).thenReturn(Future.successful(Right(Some(activeRecord))))

      service.lockStatus(personalDetailsWithNino, testMaybeCredId).value.futureValue shouldBe Right(NinoLockStatus.NotLocked)

      verify(mockNinoLockRepository, never).delete(ninoKey)
    }

    "fail closed and send NinoLockStoreError when the lock store cannot be read" in new Setup {
      when(mockNinoLockRepository.findByNinoKey(ninoKey)).thenReturn(Future.successful(Left(exception)))

      service.lockStatus(personalDetailsWithNino, testMaybeCredId).value.futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(NotLocked)")
        case Left(ex) => ex shouldBe exception
      }

      verify(mockAuditConnector).sendEvent(ninoLockStoreErrorEvent)
    }

    "fail closed and send NinoLockStoreError when deleting a stale record fails" in new Setup {
      val staleRecord: NinoLock = ninoRecord(
        lockedUntil = Some(LocalDateTime.now(ZoneOffset.UTC).minusHours(1)),
        lastFailedAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(25)
      )
      when(mockNinoLockRepository.findByNinoKey(ninoKey)).thenReturn(Future.successful(Right(Some(staleRecord))))
      when(mockNinoLockRepository.delete(ninoKey)).thenReturn(Future.successful(Left(exception)))

      service.lockStatus(personalDetailsWithNino, testMaybeCredId).value.futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(NotLocked)")
        case Left(ex) => ex shouldBe exception
      }

      verify(mockAuditConnector).sendEvent(ninoLockStoreErrorEvent)
    }
  }

  "recordOutcome" should {

    "record a nino failure without creating a lock when below the threshold" in new Setup {
      val updatedRecord: NinoLock = ninoRecord(failedAttempts = 2)
      when(mockNinoLockRepository.incrementFailedAttempts(any[String], any[LocalDateTime], any[LocalDateTime], any[Boolean]))
        .thenReturn(Future.successful(Right(Some(updatedRecord))))

      service.recordOutcome(personalDetailsWithNino, MatchFailed("no match"), testMaybeCredId).value.futureValue shouldBe Right(())

      verify(mockNinoLockRepository).incrementFailedAttempts(any[String], any[LocalDateTime], any[LocalDateTime], any[Boolean])
      verify(mockNinoLockRepository, never).createLock(any[String], any[LocalDateTime])
      verify(mockAuditConnector).sendEvent(ninoFailureRecordedEvent)
    }

    "create the nino lock on the third failed validation and send NinoLockCreated" in new Setup {
      val updatedRecord: NinoLock = ninoRecord(failedAttempts = 3)
      when(mockNinoLockRepository.incrementFailedAttempts(any[String], any[LocalDateTime], any[LocalDateTime], any[Boolean]))
        .thenReturn(Future.successful(Right(Some(updatedRecord))))
      when(mockNinoLockRepository.createLock(any[String], any[LocalDateTime]))
        .thenReturn(Future.successful(Right(Some(updatedRecord.copy(lockedUntil = Some(LocalDateTime.now(ZoneOffset.UTC).plusHours(24)))))))

      service.recordOutcome(personalDetailsWithNino, MatchFailed("no match"), testMaybeCredId).value.futureValue shouldBe Right(())

      verify(mockNinoLockRepository).createLock(any[String], any[LocalDateTime])
      verify(mockAuditConnector).sendEvent(ninoLockCreatedEvent)
    }

    "audit the failure without NinoLockCreated when a concurrent request already created the lock" in new Setup {
      val updatedRecord: NinoLock = ninoRecord(failedAttempts = 3)
      when(mockNinoLockRepository.incrementFailedAttempts(any[String], any[LocalDateTime], any[LocalDateTime], any[Boolean]))
        .thenReturn(Future.successful(Right(Some(updatedRecord))))
      when(mockNinoLockRepository.createLock(any[String], any[LocalDateTime]))
        .thenReturn(Future.successful(Right(None)))

      service.recordOutcome(personalDetailsWithNino, MatchFailed("no match"), testMaybeCredId).value.futureValue shouldBe Right(())

      verify(mockNinoLockRepository).createLock(any[String], any[LocalDateTime])
      verify(mockAuditConnector, never).sendEvent(ninoLockCreatedEvent)
      verify(mockAuditConnector).sendEvent(ninoFailureRecordedEvent)
    }

    "not increment and not audit when the record is already locked (increment guard)" in new Setup {
      when(mockNinoLockRepository.incrementFailedAttempts(any[String], any[LocalDateTime], any[LocalDateTime], any[Boolean]))
        .thenReturn(Future.successful(Right(None)))

      service.recordOutcome(personalDetailsWithNino, MatchFailed("no match"), testMaybeCredId).value.futureValue shouldBe Right(())

      verify(mockNinoLockRepository, never).createLock(any[String], any[LocalDateTime])
      verifyNoInteractions(mockAuditConnector)
    }

    "not create the lock when the incremented record is already locked" in new Setup {
      val alreadyLocked: NinoLock = ninoRecord(failedAttempts = 4, lockedUntil = Some(LocalDateTime.now(ZoneOffset.UTC).plusHours(24)))
      when(mockNinoLockRepository.incrementFailedAttempts(any[String], any[LocalDateTime], any[LocalDateTime], any[Boolean]))
        .thenReturn(Future.successful(Right(Some(alreadyLocked))))

      service.recordOutcome(personalDetailsWithNino, MatchFailed("no match"), testMaybeCredId).value.futureValue shouldBe Right(())

      verify(mockNinoLockRepository, never).createLock(any[String], any[LocalDateTime])
    }

    "not touch the lock store when the failed validation contains no nino" in new Setup {
      service.recordOutcome(personalDetailsWithPostCode, MatchFailed("no match"), testMaybeCredId).value.futureValue shouldBe Right(())

      verifyNoInteractions(mockNinoLockRepository)
    }

    "clear the below-threshold nino state when the validation is successful" in new Setup {
      when(mockNinoLockRepository.delete(ninoKey)).thenReturn(Future.successful(Right(Done)))

      service.recordOutcome(personalDetailsWithNino, MatchSuccessful(personalDetailsWithNino), testMaybeCredId).value.futureValue shouldBe Right(())

      verify(mockNinoLockRepository).delete(ninoKey)
    }

    "clear the below-threshold nino state for a deceased match" in new Setup {
      when(mockNinoLockRepository.delete(ninoKey)).thenReturn(Future.successful(Right(Done)))

      service.recordOutcome(personalDetailsWithNino, NoLivingMatch, testMaybeCredId).value.futureValue shouldBe Right(())

      verify(mockNinoLockRepository).delete(ninoKey)
    }

    "not touch the lock store when the match precondition failed (no completed validation)" in new Setup {
      service.recordOutcome(personalDetailsWithNino, MatchPreconditionFailed("underage"), testMaybeCredId).value.futureValue shouldBe Right(())

      verifyNoInteractions(mockNinoLockRepository)
    }

    "not touch the lock store for a blocked submission outcome" in new Setup {
      service.recordOutcome(personalDetailsWithNino, MatchNinoLocked, testMaybeCredId).value.futureValue shouldBe Right(())

      verifyNoInteractions(mockNinoLockRepository)
      verifyNoInteractions(mockAuditConnector)
    }

    "fail closed and send NinoLockStoreError when incrementing the failure count fails" in new Setup {
      when(mockNinoLockRepository.incrementFailedAttempts(any[String], any[LocalDateTime], any[LocalDateTime], any[Boolean]))
        .thenReturn(Future.successful(Left(exception)))

      service.recordOutcome(personalDetailsWithNino, MatchFailed("no match"), testMaybeCredId).value.futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(())")
        case Left(ex) => ex shouldBe exception
      }

      verify(mockAuditConnector).sendEvent(ninoLockStoreErrorEvent)
    }

    "fail closed and send NinoLockStoreError when clearing the nino state after a success fails" in new Setup {
      when(mockNinoLockRepository.delete(ninoKey)).thenReturn(Future.successful(Left(exception)))

      service.recordOutcome(personalDetailsWithNino, MatchSuccessful(personalDetailsWithNino), testMaybeCredId).value.futureValue match {
        case Right(_) => fail("Expected Left(Exception) but got Right(())")
        case Left(ex) => ex shouldBe exception
      }

      verify(mockAuditConnector).sendEvent(ninoLockStoreErrorEvent)
    }
  }

  trait Setup {

    val mockNinoLockRepository: NinoLockRepository = mock[NinoLockRepository]
    val mockNinoLockConfig: NinoLockConfig = mock[NinoLockConfig]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockAuditDataEventFactory: AuditDataEventFactory = mock[AuditDataEventFactory]

    when(mockNinoLockConfig.maxFailedAttempts).thenReturn(3)
    when(mockNinoLockConfig.lockDuration).thenReturn(java.time.Duration.ofHours(24))

    val service = new NinoRetryLockService(new NinoKeyService, mockNinoLockRepository, mockNinoLockConfig, mockAuditConnector, mockAuditDataEventFactory)

    when(mockAuditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[scala.concurrent.ExecutionContext])).thenReturn(Future.successful(AuditResult.Success))

    when(mockAuditDataEventFactory.createNinoFailureRecordedEvent(any[Option[String]], any[Int])(using any[HeaderCarrier], any[Request[?]]))
      .thenReturn(ninoFailureRecordedEvent)
    when(mockAuditDataEventFactory.createNinoLockCreatedEvent(any[Option[String]], any[LocalDateTime])(using any[HeaderCarrier], any[Request[?]]))
      .thenReturn(ninoLockCreatedEvent)
    when(mockAuditDataEventFactory.createNinoSubmissionBlockedEvent(any[Option[String]])(using any[HeaderCarrier], any[Request[?]]))
      .thenReturn(ninoSubmissionBlockedEvent)
    when(mockAuditDataEventFactory.createNinoLockStoreErrorEvent(any[String], any[String])(using any[HeaderCarrier], any[Request[?]]))
      .thenReturn(ninoLockStoreErrorEvent)

    def ninoRecord(failedAttempts: Int = 1,
                   lockedUntil: Option[LocalDateTime] = None,
                   lastFailedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): NinoLock =
      NinoLock(ninoKey, failedAttempts, lastFailedAt, lockedUntil, lockedUntil.getOrElse(lastFailedAt.plusHours(24)))
  }
}

object NinoRetryLockServiceSpec {

  val nino: Nino = Nino("AA000003D")
  val ninoKey: String = "AA000003"

  val personalDetailsWithNino: PersonalDetailsWithNino =
    PersonalDetailsWithNino("Jim", "Ferguson", LocalDate.of(1980, 1, 1), nino)

  val personalDetailsWithPostCode: PersonalDetailsWithPostCode =
    PersonalDetailsWithPostCode("Jim", "Ferguson", LocalDate.of(1980, 1, 1), "SE1 9NT")

  def dataEvent(auditType: String): DataEvent =
    DataEvent(auditSource = "personal-details-validation", auditType = auditType, tags = Map.empty, detail = Map.empty)

  val ninoFailureRecordedEvent: DataEvent = dataEvent("NinoFailureRecorded")
  val ninoLockCreatedEvent: DataEvent = dataEvent("NinoLockCreated")
  val ninoSubmissionBlockedEvent: DataEvent = dataEvent("NinoSubmissionBlocked")
  val ninoLockStoreErrorEvent: DataEvent = dataEvent("NinoLockStoreError")
}
