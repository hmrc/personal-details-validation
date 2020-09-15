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

import scala.language.implicitConversions

trait Options[T, VT <: ValueType[T]] extends TypeName {

  def all: Seq[VT]

  private lazy val map: Map[T, VT] = all.map(v => v.value -> v).toMap

  final def get(rawValue: T): Option[VT] = map.get(rawValue)

  final def of(rawValue: T): VT =
    get(rawValue).getOrElse(throw new IllegalArgumentException(s"'$rawValue' not known as a valid ${getClass.getSimpleName}"))
}

trait StringOptions[VT <: StringValue] extends Options[String, VT]

trait IntOptions[VT <: IntValue] extends Options[Int, VT]

trait LongOptions[VT <: LongValue] extends Options[Long, VT]

trait Apply[T, VT <: ValueType[T]] {

  self: Options[T, VT] =>

  final def apply(x: T): VT = of(x)
}

trait StringApply[VT <: StringValue] extends Apply[String, VT] {
  self: StringOptions[VT] =>
}

trait IntApply[VT <: IntValue] extends Apply[Int, VT] {
  self: IntOptions[VT] =>
}

trait LongApply[VT <: LongValue] extends Apply[Long, VT] {
  self: LongOptions[VT] =>
}
