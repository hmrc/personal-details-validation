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

package uk.gov.hmrc.personaldetailsvalidation.config

import com.typesafe.config.Config
import play.api.mvc.EssentialFilter
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}

private object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs: Config = Play.current.configuration.underlying.getConfig("controllers")
}

object MicroserviceAuditConnector extends AuditConnector {
  lazy val auditingConfig: AuditingConfig = LoadAuditingConfig(s"auditing")
}

object MicroserviceAuditFilter
  extends AuditFilter
    with AppName
    with MicroserviceFilterSupport {

  override val auditConnector: AuditConnector = MicroserviceAuditConnector

  override def controllerNeedsAuditing(controllerName: String): Boolean =
    ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter
  extends LoggingFilter
    with MicroserviceFilterSupport {

  override def controllerNeedsLogging(controllerName: String): Boolean =
    ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceGlobal
  extends DefaultMicroserviceGlobal
    with RunMode
    with MicroserviceFilterSupport {

  override val auditConnector: AuditConnector = MicroserviceAuditConnector
  override val loggingFilter: LoggingFilter = MicroserviceLoggingFilter
  override val microserviceAuditFilter: AuditFilter = MicroserviceAuditFilter
  override val authFilter: Option[EssentialFilter] = None

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] =
    app.configuration.getConfig(s"microservice.metrics")
}
