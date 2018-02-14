/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.EventsSender
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.matching.{FuturedMatchingConnector, MatchingConnector}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, PersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

@Singleton
private class FuturedPersonalDetailsValidator @Inject()(matchingConnector: FuturedMatchingConnector,
                                                        personalDetailsValidationRepository: PersonalDetailsValidationMongoRepository,
                                                        matchingEventsSender: EventsSender)
                                                       (implicit uuidProvider: UUIDProvider) extends
  PersonalDetailsValidator[Future](matchingConnector, personalDetailsValidationRepository, matchingEventsSender)

private class PersonalDetailsValidator[Interpretation[_] : Monad](matchingConnector: MatchingConnector[Interpretation],
                                                                  personalDetailsValidationRepository: PersonalDetailsValidationRepository[Interpretation],
                                                                  matchingEventsSender: EventsSender)
                                                                 (implicit uuidProvider: UUIDProvider) {

  import matchingEventsSender._
  import matchingConnector._

  def validate(personalDetails: PersonalDetails)
              (implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): EitherT[Interpretation, Exception, ValidationId] = {
    for {
      matchResult <- doMatch(personalDetails)
      personalDetailsValidation = matchResult.toPersonalDetailsValidation(optionallyHaving = personalDetails)
      _ <- personalDetailsValidationRepository.create(personalDetailsValidation)
      _ = sendEvents(matchResult, personalDetails)
    } yield personalDetailsValidation.id
  }.leftMap { error => sendErrorEvents(personalDetails); error }

  private implicit class MatchResultOps(matchResult: MatchResult) {
    def toPersonalDetailsValidation(optionallyHaving: PersonalDetails): PersonalDetailsValidation = matchResult match {
      case MatchSuccessful(_) => PersonalDetailsValidation.successful(optionallyHaving)
      case MatchFailed => PersonalDetailsValidation.failed()
    }
  }

}
