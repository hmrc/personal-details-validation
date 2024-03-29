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

package uk.gov.hmrc.personaldetailsvalidation

import play.api.Configuration
import uk.gov.hmrc.config.implicits._
import uk.gov.hmrc.config.ops._

import java.time.Duration
import javax.inject.{Inject, Singleton}

@Singleton
class PersonalDetailsValidationMongoRepositoryConfig @Inject()(configuration: Configuration) {

  lazy val collectionTtl: Duration = Duration.from(configuration.loadMandatory[Duration]("mongodb.collections.personal-details-validation.ttl"))

}
