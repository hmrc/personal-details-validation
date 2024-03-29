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

package uk.gov.hmrc.play.json

import java.time.ZoneOffset.UTC
import play.api.libs.json.{JsNumber, JsObject, Json}
import uk.gov.hmrc.datetime.CurrentTimeProvider

private[json] trait JsonObjectOps {

  implicit class JsonObjectOps(target: JsObject) {
    def withCreatedTimeStamp(fieldName: String)(implicit currentTimeProvider: CurrentTimeProvider): JsObject =
      target + (fieldName ->  Json.obj("$date" -> JsNumber(currentTimeProvider().atZone(UTC).toInstant.toEpochMilli)))
  }

}
