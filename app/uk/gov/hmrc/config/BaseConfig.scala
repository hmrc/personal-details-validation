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

package uk.gov.hmrc.config

import play.api.Configuration
import uk.gov.voa.valuetype.StringValue

case class Host(value: String) extends StringValue

trait BaseConfig {

  protected def configuration: Configuration

  private lazy val servicesKey = "microservice.services"
  private lazy val defaultProtocol: String = configuration.load(s"$servicesKey.protocol", "http")

  protected implicit lazy val hostValueFinder: String => Option[Host] = serviceName => for {
    host <- configuration.loadOptional[String](s"$servicesKey.$serviceName.host")
    port <- configuration.loadOptional[Int](s"$servicesKey.$serviceName.port")
  } yield {
    val protocol = configuration.load(s"$servicesKey.$serviceName.protocol", defaultProtocol)
    Host(s"$protocol://$host:$port")
  }
  protected implicit lazy val stringValueFinder: String => Option[String] = configuration.getString(_)
  protected implicit lazy val intValueFinder: String => Option[Int] = configuration.getInt

  protected implicit class ConfigurationOps(configuration: Configuration) {

    def loadMandatory[A](key: String)
                        (implicit find: String => Option[A]): A =
      find(key).getOrElse(throw new RuntimeException(s"Missing key: $key"))

    def loadOptional[A](key: String)
                       (implicit find: String => Option[A]): Option[A] =
      find(key)

    def load[A](key: String, default: => A)
               (implicit find: String => Option[A]): A =
      find(key).getOrElse(default)
  }
}
