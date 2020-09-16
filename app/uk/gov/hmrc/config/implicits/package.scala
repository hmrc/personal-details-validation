/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.config

import java.time.Duration

import play.api.Configuration
import uk.gov.hmrc.http.Host

package object implicits {

  import ops._

  implicit def stringValueFinder(key: String)(configuration: Configuration): Option[String] = configuration.getOptional[String](key)

  implicit def intValueFinder(key: String)(configuration: Configuration): Option[Int] = configuration.getOptional[Int](key)

  implicit def hostFinder(key: String)(configuration: Configuration): Option[Host] = for {
    servicesKey <- Some("microservice.services")
    defaultProtocol <- Some(configuration.load(s"$servicesKey.protocol", "http"))
    host <- configuration.loadOptional[String](s"$servicesKey.$key.host")
    port <- configuration.loadOptional[Int](s"$servicesKey.$key.port")
  } yield {
    val protocol = configuration.load(s"$servicesKey.$key.protocol", defaultProtocol)
    Host(s"$protocol://$host:$port")
  }

  implicit def durationFinder(key: String)(configuration: Configuration): Option[Duration] = {
    val durationValue = configuration.loadMandatory[String](key)
    Some(Duration.parse(durationValue))
  }

}
