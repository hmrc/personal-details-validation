/*
 * Copyright 2023 HM Revenue & Customs
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
import cats.implicits._
import com.google.inject.ImplementedBy
import play.api.mvc.Request
import uk.gov.hmrc.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.EventsSender
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful, NoLivingMatch}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.personaldetailsvalidation.services.RepoControlService
import uk.gov.hmrc.uuid.UUIDProvider
import uk.gov.hmrc.personaldetailsvalidation.services.Encryption

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PersonalDetailsValidatorImpl])
trait PersonalDetailsValidator {

  def validate(personalDetails: PersonalDetails, origin: Option[String], maybeCredId: Option[String])
              (implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): EitherT[Future, Exception, PersonalDetailsValidation]

  def eventDetailsToSend(matchResult: MatchResult, personalDetails: PersonalDetails): PersonalDetails

  def toPersonalDetailsValidation(matchResult: MatchResult, optionallyHaving: PersonalDetails, maybeCredId: Option[String])
                                 (implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, Exception, PersonalDetailsValidation]

}

@Singleton
class PersonalDetailsValidatorImpl @Inject() (
  matchingConnector: MatchingConnector,
  citizenDetailsConnector: CitizenDetailsConnector,
  repoControlService: RepoControlService,
  personalDetailsValidationRetryRepository: PersonalDetailsValidationRetryRepository,
  matchingEventsSender: EventsSender,
  appConfig: AppConfig)(implicit uuidProvider: UUIDProvider, encryption: Encryption) extends PersonalDetailsValidator {

  import matchingConnector._
  import matchingEventsSender._

  def validate(personalDetails: PersonalDetails, origin: Option[String], maybeCredId: Option[String])
              (implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): EitherT[Future, Exception, PersonalDetailsValidation] = {
    for {
      matchResult <- doMatch(personalDetails)
      personalDetailsValidation <- toPersonalDetailsValidation(matchResult, personalDetails, maybeCredId)
      _ <- {
        if (maybeCredId.isDefined) {
          val attempts: EitherT[Future, Exception, Int] = personalDetailsValidationRetryRepository.getAttempts(maybeCredId).map(attempts => attempts)
          personalDetailsValidation match {
            case _ : SuccessfulPersonalDetailsValidation => personalDetailsValidationRetryRepository.deleteAttempts(maybeCredId.get)
            case _ => attempts.map { attempts => personalDetailsValidationRetryRepository.recordAttempt(maybeCredId.get, attempts) }
          }
        }
        repoControlService.insertPDVAndAssociationRecord(personalDetailsValidation, maybeCredId, hc)
      }
      _ = sendEvents(addValidatedPersonalDetailsToMatchResult(personalDetailsValidation, matchResult), eventDetailsToSend(matchResult, personalDetails), origin)
    } yield personalDetailsValidation
  }.leftMap { error => sendErrorEvents(personalDetails, origin); error }

  def addValidatedPersonalDetailsToMatchResult (personalDetailsValidation: PersonalDetailsValidation, matchResult: MatchResult) : MatchResult = {
    (personalDetailsValidation, matchResult) match {
      case (spdv: SuccessfulPersonalDetailsValidation, ms:MatchSuccessful) => ms.copy(matchedPerson = spdv.personalDetails)
      case _ => matchResult
    }
  }

  def eventDetailsToSend(matchResult: MatchResult, personalDetails: PersonalDetails): PersonalDetails = {
    if (appConfig.returnNinoFromCid)
      (matchResult, personalDetails) match {
        case (MatchSuccessful(_), postCodeDetails: PersonalDetailsWithPostCode) => postCodeDetails
        case (MatchSuccessful(matchedDetails), _) => matchedDetails
        case (_, _) => personalDetails
      }
    else
      personalDetails
  }

  def toPersonalDetailsValidation(matchResult: MatchResult, optionallyHaving: PersonalDetails, maybeCredId: Option[String])
                                 (implicit ec: ExecutionContext, hc: HeaderCarrier): EitherT[Future, Exception, PersonalDetailsValidation] =
    matchResult match {
      case MatchSuccessful(pd: PersonalDetails) =>
        EitherT(toPersonalDetails(pd, optionallyHaving) map (pd => PersonalDetailsValidation.successful(pd)) map (_.asRight[Exception]))
      case NoLivingMatch =>
        EitherT(Future.successful(PersonalDetailsValidation.successful(optionallyHaving, deceased = true)).map(_.asRight[Exception]))
      case MatchFailed(_) =>
        personalDetailsValidationRetryRepository.getAttempts(maybeCredId).map(attempts => PersonalDetailsValidation.failed(maybeCredId, Some(attempts + 1)))
    }

  def toPersonalDetails(personalDetails: PersonalDetails, optionallyHaving: PersonalDetails)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PersonalDetails] =
    toPersonalDetailsWithGender ((personalDetails, optionallyHaving) match {
      case (pD: PersonalDetailsNino, oH: PersonalDetailsWithPostCode) => oH.addNino(pD.nino)
      case _ if appConfig.returnNinoFromCid => personalDetails
      case _ => optionallyHaving
    })

  def toPersonalDetailsWithGender(personalDetails: PersonalDetails)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[PersonalDetails] =
    personalDetails.maybeNino match {
      case Some(nino) =>
        citizenDetailsConnector.findDesignatoryDetails(nino)
        .map(_.fold (personalDetails)(gender => personalDetails.addGender(gender.gender)))
      case _ => Future.successful(personalDetails)
    }

}
