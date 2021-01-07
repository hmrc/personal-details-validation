/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.model

import play.api.libs.json.Format
import uk.gov.voa.valuetype.StringValue
import uk.gov.voa.valuetype.play.formats.ValueTypeFormat._

case class NonEmptyString(value: String) extends StringValue {
  require(value.trim.length > 0, s"$typeName cannot be empty")
}

object NonEmptyString {
  implicit val formatNonEmptyString: Format[NonEmptyString] = format(NonEmptyString.apply)

  implicit def toNonEmptyString(value: String): NonEmptyString = NonEmptyString(value)
}
