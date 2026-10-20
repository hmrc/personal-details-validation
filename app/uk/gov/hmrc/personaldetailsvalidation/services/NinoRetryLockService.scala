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

import cats.data.EitherT
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.*
import uk.gov.hmrc.personaldetailsvalidation.model.{NinoLock, PersonalDetails}
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory
import uk.gov.hmrc.personaldetailsvalidation.{NinoLockConfig, NinoLockRepository}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NinoRetryLockService @Inject()(ninoKeyService: NinoKeyService,
                                     ninoLockRepository: NinoLockRepository,
                                     ninoLockConfig: NinoLockConfig,
                                     auditConnector: AuditConnector,
                                     auditDataEventFactory: AuditDataEventFactory)(implicit ec: ExecutionContext) {

  import NinoRetryLockService.NinoLockStatus

  private def now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

  /** Left = lock-store read failure: caller must fail closed. Never logs the raw NINO. */
  def lockStatus(personalDetails: PersonalDetails, maybeCredId: Option[String])
                (implicit hc: HeaderCarrier, request: Request[?]): EitherT[Future, Exception, NinoLockStatus] =
    personalDetails.maybeNino match {
      case None => EitherT.rightT[Future, Exception](NinoLockStatus.NoNinoJourney)
      case Some(nino) =>
        val key = ninoKeyService.ninoKey(nino)
        EitherT(ninoLockRepository.findByNinoKey(key))
          .leftMap { ex =>
            auditConnector.sendEvent(auditDataEventFactory.createNinoLockStoreErrorEvent("read", ex.getClass.getSimpleName))
            ex
          }
          .flatMap {
            case Some(record) if record.lockedUntil.exists(_.isAfter(now)) =>
              EitherT.rightT[Future, Exception] {
                auditConnector.sendEvent(auditDataEventFactory.createNinoSubmissionBlockedEvent(maybeCredId))
                NinoLockStatus.Locked(record.lockedUntil.get)
              }
            case Some(record) if isStale(record) =>
              EitherT(ninoLockRepository.delete(key))
                .leftMap { ex =>
                  auditConnector.sendEvent(auditDataEventFactory.createNinoLockStoreErrorEvent("delete-stale", ex.getClass.getSimpleName))
                  ex
                }
                .map(_ => NinoLockStatus.NotLocked)
            case _ => EitherT.rightT[Future, Exception](NinoLockStatus.NotLocked)
          }
    }

  private def isStale(record: NinoLock): Boolean =
    record.lockedUntil.forall(!_.isAfter(now)) &&
      record.lastFailedAt.isBefore(now.minus(ninoLockConfig.lockDuration))

  /** Records the NINO-side outcome of a completed validation. Left = lock-store write failure: fail closed. */
  def recordOutcome(personalDetails: PersonalDetails, matchResult: MatchResult, maybeCredId: Option[String])
                   (implicit hc: HeaderCarrier, request: Request[?]): EitherT[Future, Exception, Unit] =
    (personalDetails.maybeNino, matchResult) match {
      case (None, _) => EitherT.rightT[Future, Exception](())
      case (Some(nino), MatchFailed(_)) =>
        val key = ninoKeyService.ninoKey(nino)
        val failedAt = now
        EitherT(ninoLockRepository.incrementFailedAttempts(key, failedAt, failedAt.plus(ninoLockConfig.lockDuration)))
          .leftMap { ex =>
            auditConnector.sendEvent(auditDataEventFactory.createNinoLockStoreErrorEvent("write", ex.getClass.getSimpleName))
            ex
          }
          .flatMap {
            case Some(updated) if updated.failedAttempts >= ninoLockConfig.maxFailedAttempts && updated.lockedUntil.isEmpty =>
              val lockedUntil = failedAt.plus(ninoLockConfig.lockDuration)
              EitherT(ninoLockRepository.createLock(key, lockedUntil))
                .leftMap { ex =>
                  auditConnector.sendEvent(auditDataEventFactory.createNinoLockStoreErrorEvent("write", ex.getClass.getSimpleName))
                  ex
                }
                .map {
                  case Some(_) =>
                    auditConnector.sendEvent(auditDataEventFactory.createNinoLockCreatedEvent(maybeCredId, lockedUntil))
                    ()
                  case None =>
                    // lost the createLock race: the lock now exists, but this failure was still counted
                    auditConnector.sendEvent(auditDataEventFactory.createNinoFailureRecordedEvent(maybeCredId, updated.failedAttempts))
                    ()
                }
            case Some(updated) =>
              EitherT.rightT[Future, Exception] {
                auditConnector.sendEvent(auditDataEventFactory.createNinoFailureRecordedEvent(maybeCredId, updated.failedAttempts))
                ()
              }
            case None => EitherT.rightT[Future, Exception](())
          }
      case (Some(nino), MatchSuccessful(_) | NoLivingMatch) =>
        val key = ninoKeyService.ninoKey(nino)
        EitherT(ninoLockRepository.delete(key))
          .leftMap { ex =>
            auditConnector.sendEvent(auditDataEventFactory.createNinoLockStoreErrorEvent("delete", ex.getClass.getSimpleName))
            ex
          }
          .map(_ => ())
      case (Some(_), MatchPreconditionFailed(_) | MatchPreconditionSuccessful | MatchNinoLocked) =>
        EitherT.rightT[Future, Exception](())
    }
}

object NinoRetryLockService {
  sealed trait NinoLockStatus
  object NinoLockStatus {
    case object NoNinoJourney extends NinoLockStatus
    case object NotLocked extends NinoLockStatus
    case class Locked(lockedUntil: LocalDateTime) extends NinoLockStatus
  }
}
