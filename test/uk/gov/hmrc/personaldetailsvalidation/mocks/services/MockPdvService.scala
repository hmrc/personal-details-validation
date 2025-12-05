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
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetailsValidation, ValidationId}
import uk.gov.hmrc.personaldetailsvalidation.services.PersonalDetailsValidatorService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

object MockPdvService {

  def insertRecord(service: PersonalDetailsValidatorService, personalDetails: PersonalDetailsValidation)(returnValue: Done): OngoingStubbing[EitherT[Future, Exception, Done]] = {
    when(service.insertRecord(eqTo(personalDetails))(using any[ExecutionContext]))
      .thenReturn(EitherT.pure[Future, Exception](returnValue))
  }

  def getRecord(service: PersonalDetailsValidatorService, personalDetailsValidationId: ValidationId)(returnValue: Option[PersonalDetailsValidation]): OngoingStubbing[Future[Option[PersonalDetailsValidation]]] = {
    when(service.getRecord(eqTo(personalDetailsValidationId))(using any[ExecutionContext]))
      .thenReturn(Future.successful(returnValue))
  }

}
