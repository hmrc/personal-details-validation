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

import cats.data.EitherT
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar._
import org.mockito.MockitoSugar._
import org.mockito.stubbing.ScalaOngoingStubbing
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.personaldetailsvalidation.PdvRepository
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, ValidationId}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global


object MockPdvRepository {

  def create(mockInstance: PdvRepository, personalDetails: PersonalDetailsValidation): ScalaOngoingStubbing[EitherT[Future, Exception, Done]] = {
    when(mockInstance.create(eqTo(personalDetails))(any[ExecutionContext]))
      .thenReturn(EitherT.pure[Future, Exception](Done))
  }

  def get(mockInstance: PdvRepository, personalDetailsValidationId: ValidationId)(returnValue: PersonalDetailsValidation): ScalaOngoingStubbing[Future[Option[PersonalDetailsValidation]]] = {
    when(mockInstance.get(eqTo(personalDetailsValidationId))(any[ExecutionContext]))
      .thenReturn(Future.successful(Some(returnValue)))
  }

  def getError(mockInstance: PdvRepository, personalDetailsValidationId: ValidationId): ScalaOngoingStubbing[Future[Option[PersonalDetailsValidation]]] = {
    when(mockInstance.get(eqTo(personalDetailsValidationId))(any[ExecutionContext]))
      .thenReturn(Future.successful(None))
  }

}
