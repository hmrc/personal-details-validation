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

import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.when
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

object MockAuthConnector {

  def authoriseSuccess(mockAuthConnector: AuthConnector, creds: Credentials = Credentials("test", "test")): Unit = {
    when(mockAuthConnector.authorise[Option[Credentials]](
      any[Predicate],
      any[Retrieval[Option[Credentials]]]
    )(using any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(Some(creds)))
  }

}
