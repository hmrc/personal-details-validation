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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.{AuditDataEventFactory, NinoLockOutcome}
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.*
import uk.gov.hmrc.personaldetailsvalidation.model.{NinoLock, PersonalDetails}
import uk.gov.hmrc.personaldetailsvalidation.{NinoLockConfig, NinoLockRepository}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NinoRetryLockService @Inject()(ninoLockRepository: NinoLockRepository,
                                     ninoLockConfig: NinoLockConfig,
                                     auditConnector: AuditConnector,
                                     auditDataEventFactory: AuditDataEventFactory)(implicit ec: ExecutionContext) {

  import NinoRetryLockService.{NinoLockStatus, ninoKey}

  private def now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
  
  def lockStatus(personalDetails: PersonalDetails, maybeCredId: Option[String])
                (implicit hc: HeaderCarrier, request: Request[?]): EitherT[Future, Exception, NinoLockStatus] =
    personalDetails.maybeNino match {
      case None => EitherT.rightT[Future, Exception](NinoLockStatus.NoNinoJourney)
      case Some(nino) =>
        val key = nino.ninoKey
        EitherT(ninoLockRepository.findByNinoKey(key))
          .leftMap { ex =>
            auditConnector.sendEvent(auditDataEventFactory.createNinoLockEvent(personalDetails, maybeCredId, NinoLockOutcome.LockStoreError("read", ex.getClass.getSimpleName, ex.getMessage)))
            ex
          }
          .flatMap {
            case Some(record) if record.isLocked(now) =>
              EitherT.rightT[Future, Exception] {
                auditConnector.sendEvent(auditDataEventFactory.createNinoLockEvent(personalDetails, maybeCredId, NinoLockOutcome.SubmissionBlocked))
                NinoLockStatus.Locked(record.lockedUntil.get)
              }
            case Some(record) if isStale(record) =>
              EitherT(ninoLockRepository.delete(key))
                .leftMap { ex =>
                  auditConnector.sendEvent(auditDataEventFactory.createNinoLockEvent(personalDetails, maybeCredId, NinoLockOutcome.LockStoreError("delete-stale", ex.getClass.getSimpleName, ex.getMessage)))
                  ex
                }
                .map(_ => NinoLockStatus.NotLocked)
            case _ => EitherT.rightT[Future, Exception](NinoLockStatus.NotLocked)
          }
    }

  private def isStale(record: NinoLock): Boolean =
    !record.isLocked(now) &&
      record.lastFailedAt.isBefore(now.minus(ninoLockConfig.lockDuration))
  
  def recordOutcome(personalDetails: PersonalDetails, matchResult: MatchResult, maybeCredId: Option[String])
                   (implicit hc: HeaderCarrier, request: Request[?]): EitherT[Future, Exception, Unit] =
    (personalDetails.maybeNino, matchResult) match {
      case (None, _) => EitherT.rightT[Future, Exception](())
      case (Some(nino), MatchFailed(_)) =>
        val key = nino.ninoKey
        val failedAt = now
        EitherT(ninoLockRepository.incrementFailedAttempts(key, failedAt, failedAt.plus(ninoLockConfig.lockDuration)))
          .leftMap { ex =>
            auditConnector.sendEvent(auditDataEventFactory.createNinoLockEvent(personalDetails, maybeCredId, NinoLockOutcome.LockStoreError("write", ex.getClass.getSimpleName, ex.getMessage)))
            ex
          }
          .flatMap {
            case Some(updated) if updated.failedAttempts >= ninoLockConfig.maxFailedAttempts && updated.lockedUntil.isEmpty =>
              val lockedUntil = failedAt.plus(ninoLockConfig.lockDuration)
              EitherT(ninoLockRepository.createLock(key, lockedUntil))
                .leftMap { ex =>
                  auditConnector.sendEvent(auditDataEventFactory.createNinoLockEvent(personalDetails, maybeCredId, NinoLockOutcome.LockStoreError("write", ex.getClass.getSimpleName, ex.getMessage)))
                  ex
                }
                .map {
                  case Some(_) =>
                    auditConnector.sendEvent(auditDataEventFactory.createNinoLockEvent(personalDetails, maybeCredId, NinoLockOutcome.LockCreated(lockedUntil)))
                    ()
                  case None =>
                    auditConnector.sendEvent(auditDataEventFactory.createNinoLockEvent(personalDetails, maybeCredId, NinoLockOutcome.FailureRecorded(updated.failedAttempts)))
                    ()
                }
            case Some(updated) =>
              EitherT.rightT[Future, Exception] {
                auditConnector.sendEvent(auditDataEventFactory.createNinoLockEvent(personalDetails, maybeCredId, NinoLockOutcome.FailureRecorded(updated.failedAttempts)))
                ()
              }
            case None => EitherT.rightT[Future, Exception](())
          }
      case (Some(nino), MatchSuccessful(_) | NoLivingMatch) =>
        val key = nino.ninoKey
        EitherT(ninoLockRepository.delete(key))
          .leftMap { ex =>
            auditConnector.sendEvent(auditDataEventFactory.createNinoLockEvent(personalDetails, maybeCredId, NinoLockOutcome.LockStoreError("delete", ex.getClass.getSimpleName, ex.getMessage)))
            ex
          }
          .map(_ => ())
      case (Some(_), MatchPreconditionFailed(_) | MatchPreconditionSuccessful | MatchNinoLocked) =>
        EitherT.rightT[Future, Exception](())
    }
}

object NinoRetryLockService {
  extension (nino: Nino)
    def ninoKey: String =
      nino.value.replaceAll("\\s", "").toUpperCase.take(8)

  sealed trait NinoLockStatus
  object NinoLockStatus {
    case object NoNinoJourney extends NinoLockStatus
    case object NotLocked extends NinoLockStatus
    case class Locked(lockedUntil: LocalDateTime) extends NinoLockStatus
  }
}
