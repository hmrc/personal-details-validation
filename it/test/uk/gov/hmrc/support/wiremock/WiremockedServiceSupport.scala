/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.support.wiremock

import uk.gov.hmrc.support.wiremock.WiremockConfiguration.*

trait WiremockedServiceSupport {

  val wiremockedServices: List[String] = List[String]()

  lazy val wiremockedServicesConfiguration: Map[String, Any] = {
    val servicesConfig = wiremockedServices.foldLeft(Map.empty[String, Any]) { (result, service) =>
      result ++ Map(
        s"microservice.services.$service.host" -> wiremockHost,
        s"microservice.services.$service.port" -> wiremockPort
      )
    }

    val auditingConfig = Map[String, Any](
      "auditing.consumer.baseUri.host" -> wiremockHost,
      "auditing.consumer.baseUri.port" -> wiremockPort
    )

    servicesConfig ++ auditingConfig
  }
}
