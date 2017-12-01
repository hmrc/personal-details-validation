/*
 * Copyright 2017 HM Revenue & Customs
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

import com.google.inject.Inject
import play.api.Configuration
import play.api.http.Status._
import play.api.mvc.RequestHeader
import play.api.mvc.Results.NotFound
import uk.gov.hmrc.play.pathbinders.PathBinders.Errors.NOT_A_VALID_UUID
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.JsonErrorHandler

import scala.concurrent.Future

class ErrorHandler @Inject()(configuration: Configuration, auditConnector: AuditConnector) extends JsonErrorHandler(configuration, auditConnector) {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    if (statusCode == BAD_REQUEST && message.endsWith(NOT_A_VALID_UUID)) {
      Future.successful(NotFound)
    } else {
      super.onClientError(request, statusCode, message)
    }
  }
}
