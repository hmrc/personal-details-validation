/*
 * Copyright 2021 HM Revenue & Customs
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
import javax.inject.{Inject, Singleton}
import play.api.mvc.Request
import uk.gov.hmrc.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.EventsSender
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.matching.{FuturedMatchingConnector, MatchingConnector}
import uk.gov.hmrc.personaldetailsvalidation.model._
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@Singleton
class FuturedPersonalDetailsValidator @Inject()(
  matchingConnector: FuturedMatchingConnector,
  personalDetailsValidationRepository: PersonalDetailsValidationMongoRepository,
  matchingEventsSender: EventsSender,
  appConfig: AppConfig
)(
  implicit uuidProvider: UUIDProvider, ec: ExecutionContext
) extends PersonalDetailsValidator[Future](
  matchingConnector,
  personalDetailsValidationRepository,
  matchingEventsSender,
  appConfig
)

class PersonalDetailsValidator[Interpretation[_] : Monad](
  matchingConnector: MatchingConnector[Interpretation],
  personalDetailsValidationRepository: PersonalDetailsValidationRepository[Interpretation],
  matchingEventsSender: EventsSender,
  appConfig: AppConfig)
(
  implicit uuidProvider: UUIDProvider
) {

  import matchingConnector._
  import matchingEventsSender._

  def validate(personalDetails: PersonalDetails, origin: Option[String])
              (implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): EitherT[Interpretation, Exception, PersonalDetailsValidation] = {
    sendBeginEvent(origin)
    for {
      matchResult <- doMatch(personalDetails)
      personalDetailsValidation = toPersonalDetailsValidation(matchResult, personalDetails)
      _ <- personalDetailsValidationRepository.create(personalDetailsValidation)
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

  def toPersonalDetailsValidation(matchResult: MatchResult, optionallyHaving: PersonalDetails): PersonalDetailsValidation = {
    (matchResult, optionallyHaving) match {
      case (MatchSuccessful(matchingPerson: PersonalDetailsNino), other: PersonalDetailsWithPostCode) =>
        PersonalDetailsValidation.successful(other.addNino(matchingPerson.nino))
      case (MatchSuccessful(matchingPerson), _) if appConfig.returnNinoFromCid => PersonalDetailsValidation.successful(matchingPerson)
      case (MatchSuccessful(_), _) => PersonalDetailsValidation.successful(optionallyHaving)
      case (MatchFailed(_), _) => PersonalDetailsValidation.failed()
    }
  }
}
