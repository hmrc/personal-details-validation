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

package uk.gov.voa.valuetype.constraints

import uk.gov.voa.valuetype.{IntValue, LongValue, ValueType}

trait PositiveInt extends IntValue {

  require(value > 0, s"$typeName's value has to be positive")

}

trait PositiveLong extends LongValue {

  require(value > 0, s"$typeName's value has to be positive")

}

trait PositiveBigDecimal {

  self: ValueType[BigDecimal] =>

  require(value > 0, s"$typeName's value has to be positive")

}
