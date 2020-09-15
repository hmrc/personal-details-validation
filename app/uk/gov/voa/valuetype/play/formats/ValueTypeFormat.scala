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

package uk.gov.voa.valuetype.play.formats

import play.api.libs.json._
import uk.gov.voa.valuetype.{StringValue, ValueType}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ValueTypeFormat extends ValueTypeFormat

trait ValueTypeFormat {

  implicit def parseString: PartialFunction[JsValue, String] = {
    case JsString(value) if !value.isEmpty => value
  }

  implicit def parseInt: PartialFunction[JsValue, Int] = {
    case JsNumber(value) if value.isValidInt => value.toInt
  }

  implicit def parseLong: PartialFunction[JsValue, Long] = {
    case JsNumber(value) if value.isValidLong => value.toLong
  }

  implicit def parseBigDecimal: PartialFunction[JsValue, BigDecimal] = {
    case JsNumber(value) => value
  }

  implicit def parseBoolean: PartialFunction[JsValue, Boolean] = {
    case JsBoolean(value) => value
  }

  implicit val stringToJson = JsString.apply _
  implicit val intToJson = (value: Int) => JsNumber.apply(value)
  implicit val longToJson = (value: Long) => JsNumber.apply(value)
  implicit val bigDecimalToJson = (value: BigDecimal) => JsNumber.apply(value)
  implicit val booleanToJson = JsBoolean.apply _

  def valueTypeReadsFor[T, V <: ValueType[T]](instantiateFromSimpleType: T => V)
                                             (implicit parse: PartialFunction[JsValue, T]) =
    new Reads[V] {

      def reads(json: JsValue): JsResult[V] = Try(parse(json)).flatMap(t => Try(instantiateFromSimpleType(t))) match {
        case Success(value) => JsSuccess(value)
        case Failure(e) => JsError(e.getMessage)
      }
    }

  def valueTypeWritesFor[T, V <: ValueType[T]](implicit toJson: T => JsValue) = new Writes[V] {

    def writes(value: V): JsValue = toJson(value.value)

  }

  def valueTypeWritesFor[V <: StringValue](implicit classTag: ClassTag[V]) = new Writes[V] {

    def writes(value: V): JsValue = JsString(value.value)

  }

  def format[T, V <: ValueType[T]](instantiateFromSimpleType: T => V)
                                  (implicit parse: PartialFunction[JsValue, T],
                                   toJson: T => JsValue) =
    Format[V](valueTypeReadsFor(instantiateFromSimpleType), valueTypeWritesFor[T, V])
}
