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

package uk.gov.voa.valuetype

import scala.math.BigDecimal.RoundingMode
import scala.math.BigDecimal.RoundingMode.RoundingMode

trait ValueType[T] extends TypeName {

  def value: T

  override def toString = value.toString

}

trait StringValue extends ValueType[String]

trait IntValue extends ValueType[Int]

trait LongValue extends ValueType[Long]

trait BooleanValue extends ValueType[Boolean]

trait BigDecimalValue extends ValueType[BigDecimal]

trait RoundedBigDecimalValue extends ValueType[BigDecimal] {

  protected[this] def nonRoundedValue: BigDecimal

  val decimalPlaces: Int = 2
  val roundingMode: RoundingMode = RoundingMode.HALF_UP

  final lazy val value = nonRoundedValue.setScale(decimalPlaces, roundingMode)

  final override def equals(other: Any): Boolean = other match {
    case that: RoundedBigDecimalValue => isOfThisInstance(that) && value == that.value
    case _ => false
  }

  protected[this] def isOfThisInstance(other: RoundedBigDecimalValue): Boolean

  final override def hashCode: Int = (41 * value).toInt
}
