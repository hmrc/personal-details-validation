/*
 * Copyright 2017 HM Revenue & Customs
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

import akka.Done
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.connectors.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.connectors.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.connectors.MatchingConnector.MatchResult.{MatchFailed, MatchSuccessful}
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, PersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.uuid.UUIDProvider

import scala.concurrent.{ExecutionContext, Future}

@Singleton
private class PersonalDetailsValidator @Inject()(private val matchingConnector: MatchingConnector,
                                                 private val personalDetailsValidationRepository: PersonalDetailsValidationRepository)
                                                (implicit uuidProvider: UUIDProvider) {

  def validate(personalDetails: PersonalDetails)
              (implicit headerCarrier: HeaderCarrier,
               executionContext: ExecutionContext): Future[ValidationId] = for {
    matchResult <- matchingConnector.doMatch(personalDetails)
    personalDetailsValidation = matchResult.toPersonalDetailsValidation(optionallyHaving = personalDetails)
    Done <- personalDetailsValidationRepository.create(personalDetailsValidation)
  } yield personalDetailsValidation.id

  private implicit class MatchResultOps(matchResult: MatchResult) {
    def toPersonalDetailsValidation(optionallyHaving: PersonalDetails): PersonalDetailsValidation = matchResult match {
      case MatchSuccessful => PersonalDetailsValidation.successful(optionallyHaving)
      case MatchFailed => PersonalDetailsValidation.failed()
    }
  }
}
