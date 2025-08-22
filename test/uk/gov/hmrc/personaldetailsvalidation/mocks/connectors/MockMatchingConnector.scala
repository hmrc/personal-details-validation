/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.personaldetailsvalidation.mocks.connectors

import cats.data.EitherT
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.MockitoSugar.when
import org.mockito.stubbing.ScalaOngoingStubbing
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetails

import scala.concurrent.ExecutionContext.Implicits.{global => executionContext}
import scala.concurrent.{ExecutionContext, Future}

object MockMatchingConnector {

  val mockInstance: MatchingConnector = mock[MatchingConnector]

  def doMatch(connector: MatchingConnector, personalDetails: PersonalDetails)(returnValue: MatchResult): ScalaOngoingStubbing[EitherT[Future, Exception, MatchResult]] = {
    when(connector.doMatch(eqTo(personalDetails))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(
        EitherT.rightT[Future, Exception](returnValue))
  }

  def doMatchError(connector: MatchingConnector, personalDetails: PersonalDetails)(returnValue: RuntimeException): ScalaOngoingStubbing[EitherT[Future, Exception, MatchResult]] = {
    when(connector.doMatch(eqTo(personalDetails))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(
        EitherT.leftT[Future, MatchResult](returnValue))
  }

}
