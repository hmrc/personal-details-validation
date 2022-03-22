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

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import play.api.mvc.Request
import uk.gov.hmrc.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.EventsSender
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.matching.{MatchingConnectorImpl, MatchingConnector}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.uuid.UUIDProvider

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@Singleton
class FuturedPersonalDetailsValidator @Inject()(
  matchingConnector: MatchingConnector,
  personalDetailsValidationRepository: PersonalDetailsValidationRepository,
  personalDetailsValidationRetryRepository: PersonalDetailsValidationRetryRepository,
  matchingEventsSender: EventsSender,
  appConfig: AppConfig
)(
  implicit uuidProvider: UUIDProvider, ec: ExecutionContext
) extends PersonalDetailsValidator[Future](
  matchingConnector,
  personalDetailsValidationRepository,
  personalDetailsValidationRetryRepository,
  matchingEventsSender,
  appConfig
)

class PersonalDetailsValidator[Interpretation[_] : Monad](
  matchingConnector: MatchingConnector,
  personalDetailsValidationRepository: PdvRepository,
  personalDetailsValidationRetryRepository: PersonalDetailsValidationRetryRepository,
  matchingEventsSender: EventsSender,
  appConfig: AppConfig)(implicit uuidProvider: UUIDProvider) {

  import matchingConnector._
  import matchingEventsSender._

  def validate(personalDetails: PersonalDetails, origin: Option[String], maybeCredId: Option[String])
              (implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): EitherT[Future, Exception, PersonalDetailsValidation] = {
    sendBeginEvent(origin)
    for {
      matchResult <- doMatch(personalDetails)
      personalDetailsValidation <- toPersonalDetailsValidation(matchResult, personalDetails, maybeCredId)
      _ <- {
        if (maybeCredId.isDefined) { personalDetailsValidationRetryRepository.recordAttempt(maybeCredId.get) }
        personalDetailsValidationRepository.create(personalDetailsValidation)
      }
      _ = sendEvents(matchResult, eventDetailsToSend(matchResult, personalDetails), origin)
    } yield personalDetailsValidation
  }.leftMap { error => sendErrorEvents(personalDetails, origin); error }

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
                                 (implicit ec: ExecutionContext): EitherT[Future, Exception, PersonalDetailsValidation] = {
    (matchResult, optionallyHaving) match {
      case (MatchSuccessful(matchingPerson: PersonalDetailsNino), other: PersonalDetailsWithPostCode) =>
        EitherT.fromEither[Future](
          PersonalDetailsValidation.successful(other.addNino(matchingPerson.nino)).asRight[Exception]
        )
      case (MatchSuccessful(matchingPerson), _) if appConfig.returnNinoFromCid =>
        EitherT.fromEither[Future](
          PersonalDetailsValidation.successful(matchingPerson).asRight[Exception]
        )
      case (MatchSuccessful(_), _) =>
        EitherT.fromEither[Future](
          PersonalDetailsValidation.successful(optionallyHaving).asRight[Exception]
        )
      case (MatchFailed(_), _) =>
        personalDetailsValidationRetryRepository.getAttempts(maybeCredId).map(attempts => PersonalDetailsValidation.failed(maybeCredId, Some(attempts + 1)))
    }
  }
}
