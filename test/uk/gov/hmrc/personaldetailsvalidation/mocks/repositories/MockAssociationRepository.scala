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

package uk.gov.hmrc.personaldetailsvalidation.mocks.repositories

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import uk.gov.hmrc.personaldetailsvalidation.AssociationRepository
import uk.gov.hmrc.personaldetailsvalidation.model.Association

import scala.concurrent.Future

object MockAssociationRepository {

  def insertRecord(repository: AssociationRepository, association: Association)(returnValue: Future[Unit]): OngoingStubbing[Future[Unit]] = {
    when(repository.insertRecord(eqTo(association)))
      .thenReturn(returnValue)
  }

  def getRecord(repository: AssociationRepository)(returnValue: Option[Association]): OngoingStubbing[Future[Option[Association]]] = {
    when(repository.getRecord(any[String], any[String]))
      .thenReturn(Future.successful(returnValue))
  }

}
