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
import uk.gov.voa.valuetype._

import scala.language.implicitConversions

object OptionsFormat extends OptionsFormat

trait OptionsFormat {

  implicit def stringOptionsFormat[VT <: StringValue](options: Options[String, VT]): Format[VT] = new Format[VT] {

    def reads(json: JsValue): JsResult[VT] = json match {
      case JsString(value) => options.get(value) match {
        case Some(foundOption) => JsSuccess(foundOption)
        case _ => JsError(s"'$value' not a valid option")
      }
      case other => JsError(s"[$other.getClass] is not a valid value: Expected a String option")
    }

    def writes(option: VT): JsValue = JsString(option.value)
  }

  implicit def intOptionsFormat[VT <: IntValue](options: Options[Int, VT]): Format[VT] = new Format[VT] {

    def reads(json: JsValue): JsResult[VT] = json match {
      case JsNumber(value) if value.isValidInt => options.get(value.toInt) match {
        case Some(foundOption) => JsSuccess(foundOption)
        case _ => JsError(s"'$value' not a valid option")
      }
      case JsNumber(nonInt) => JsError(s"[$nonInt] is not a valid value: Expected an Integer option")
      case other => JsError(s"[$other.getClass] is not a valid value: Expected an Integer option")
    }

    def writes(option: VT): JsValue = JsNumber(option.value)
  }

  implicit def longOptionsFormat[VT <: LongValue](options: Options[Long, VT]): Format[VT] = new Format[VT] {

    def reads(json: JsValue): JsResult[VT] = json match {
      case JsNumber(value) if value.isValidLong => options.get(value.toLong) match {
        case Some(foundOption) => JsSuccess(foundOption)
        case _ => JsError(s"'$value' not a valid option")
      }
      case JsNumber(nonLong) => JsError(s"[$nonLong] is not a valid value: Expected a Long option")
      case other => JsError(s"[$other.getClass] is not a valid value: Expected an Long option")
    }

    def writes(option: VT): JsValue = JsNumber(option.value)
  }

  def optionsFormat[T, VT <: ValueType[T]](options: Options[T, VT])
                                          (implicit valueTypeFormat: Options[T, VT] => Format[VT]): Format[VT] =
    valueTypeFormat(options)
}
