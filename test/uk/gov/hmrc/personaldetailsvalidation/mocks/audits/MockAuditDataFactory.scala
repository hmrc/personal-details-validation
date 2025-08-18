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

package uk.gov.hmrc.personaldetailsvalidation.mocks.audits

import org.mockito.MockitoSugar.when
import org.scalatestplus.mockito.MockitoSugar.mock
import org.mockito.ArgumentMatchersSugar.{any, eqTo}
import org.mockito.stubbing.ScalaOngoingStubbing
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.audit.AuditDataEventFactory
import uk.gov.hmrc.personaldetailsvalidation.matching.MatchingConnector.MatchResult
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetails
import uk.gov.hmrc.play.audit.model.DataEvent


object MockAuditDataFactory {

  val mockInstance: AuditDataEventFactory = mock[AuditDataEventFactory]

  def createEvent(matchResult: MatchResult, personalDetails: PersonalDetails)(returnValue: DataEvent): ScalaOngoingStubbing[DataEvent] = {
    when(mockInstance.createEvent(eqTo(matchResult), eqTo(personalDetails))(any[HeaderCarrier], any[Request[_]]))
      .thenReturn(returnValue)
  }

  def createErrorEvent(personalDetails: PersonalDetails)(returnValue: DataEvent): ScalaOngoingStubbing[DataEvent] = {
    when(mockInstance.createErrorEvent(eqTo(personalDetails))(any[HeaderCarrier], any[Request[_]]))
      .thenReturn(returnValue)
  }


}
