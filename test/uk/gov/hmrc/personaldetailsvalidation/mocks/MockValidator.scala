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

package uk.gov.hmrc.personaldetailsvalidation.mocks

import cats.data.EitherT
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import play.api.mvc.Request
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier}
import uk.gov.hmrc.personaldetailsvalidation.PersonalDetailsValidator
import uk.gov.hmrc.personaldetailsvalidation.model.{PersonalDetails, PersonalDetailsValidation}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

object MockValidator {

  def validate(validator: PersonalDetailsValidator,
               personalDetails: PersonalDetails,
               origin: Option[String],
               maybeCredId: Option[String])(returnValue: PersonalDetailsValidation): OngoingStubbing[EitherT[Future, Exception, PersonalDetailsValidation]] = {
    when(validator.validate(eqTo(personalDetails), eqTo(origin), eqTo(maybeCredId))(using any[HeaderCarrier], any[Request[?]], any[ExecutionContext]))
      .thenReturn(EitherT.rightT[Future, Exception](returnValue))
  }

  def validateNoId(validator: PersonalDetailsValidator,
                   personalDetailsValidation:PersonalDetailsValidation,
                   origin: Option[String]): OngoingStubbing[EitherT[Future, Exception, PersonalDetailsValidation]] = {
    when(validator.validate(any[PersonalDetails], eqTo(origin), any[Option[String]])(using any[HeaderCarrier], any[Request[?]], any[ExecutionContext]))
      .thenReturn(EitherT.rightT[Future, Exception](personalDetailsValidation))
  }

  def validateException(validator: PersonalDetailsValidator,
               personalDetails: PersonalDetails,
               origin: Option[String],
               maybeCredId: Option[String])(returnValue: BadGatewayException): OngoingStubbing[EitherT[Future, Exception, PersonalDetailsValidation]] = {
    when(validator.validate(eqTo(personalDetails), eqTo(origin), eqTo(maybeCredId))(using any[HeaderCarrier], any[Request[?]], any[ExecutionContext]))
      .thenReturn(EitherT.leftT[Future, PersonalDetailsValidation](returnValue))
  }

}
