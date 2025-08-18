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

package uk.gov.hmrc.personaldetailsvalidation.mocks.services

import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.MockitoSugar.when
import org.mockito.stubbing.ScalaOngoingStubbing
import uk.gov.hmrc.personaldetailsvalidation.model.Association
import uk.gov.hmrc.personaldetailsvalidation.services.AssociationService

import scala.concurrent.Future

object MockAssociationService {

  def insertRecord(service: AssociationService, association: Association): ScalaOngoingStubbing[Future[Unit]] = {
    when(service.insertRecord(eqTo(association)))
      .thenReturn(Future.successful(()))
  }

}
