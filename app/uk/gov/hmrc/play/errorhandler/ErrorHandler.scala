/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.play.errorhandler

import play.api.http.Status.*
import play.api.mvc.Results.NotFound
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.play.pathbinders.PathBinders.Errors.NOT_A_VALID_UUID

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ErrorHandler @Inject()(auditConnector: AuditConnector, httpAuditEvent: HttpAuditEvent, configuration: Configuration)
                            (implicit ec: ExecutionContext)
  extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration) with Logging {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    if (statusCode == BAD_REQUEST && message.endsWith(NOT_A_VALID_UUID)) {
      logger.info(s"status code was $BAD_REQUEST and message ended with $NOT_A_VALID_UUID, returning $NOT_FOUND")
      Future.successful(NotFound)
    } else {
      super.onClientError(request, statusCode, message)
    }
  }
}
