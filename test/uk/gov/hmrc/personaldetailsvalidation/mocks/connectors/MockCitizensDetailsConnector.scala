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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.CitizenDetailsConnector
import uk.gov.hmrc.personaldetailsvalidation.model.Gender

import scala.concurrent.{ExecutionContext, Future}

object MockCitizensDetailsConnector {

  def findDesignatoryDetails(connector: CitizenDetailsConnector)(returnValue: Option[Gender]): OngoingStubbing[Future[Option[Gender]]] = {
    when(connector.findDesignatoryDetails(any[Nino])(using any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(returnValue))

  }

}
