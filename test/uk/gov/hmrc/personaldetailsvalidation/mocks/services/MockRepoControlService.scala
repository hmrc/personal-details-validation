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

import cats.data.EitherT
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.personaldetailsvalidation.model.PersonalDetailsValidation
import uk.gov.hmrc.personaldetailsvalidation.services.{Encryption, RepoControlService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

object MockRepoControlService {

  def insertPDVAndAssociationRecord(service: RepoControlService,
                                    opCredID: Option[String]
                                   )(returnValue: Done): OngoingStubbing[EitherT[Future, Exception, Done]] = {
    when(service.insertPDVAndAssociationRecord(any[PersonalDetailsValidation], eqTo(opCredID))(using any[HeaderCarrier], any[ExecutionContext], any[Encryption]))
      .thenReturn(EitherT.rightT[Future, Exception](returnValue))
  }

  def insertPDVAndAssociationRecordError(service: RepoControlService,
                                         opCredID: Option[String])(returnValue: RuntimeException): OngoingStubbing[EitherT[Future, Exception, Done]] = {
    when(service.insertPDVAndAssociationRecord(any[PersonalDetailsValidation], eqTo(opCredID))(using any[HeaderCarrier], any[ExecutionContext], any[Encryption]))
      .thenReturn(EitherT.leftT[Future, Done](returnValue))
  }

}
