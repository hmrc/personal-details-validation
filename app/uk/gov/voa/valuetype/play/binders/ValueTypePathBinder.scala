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

package uk.gov.voa.valuetype.play.binders

import play.api.mvc.PathBindable
import uk.gov.voa.valuetype.ValueType

import scala.util.{Failure, Success, Try}

object ValueTypePathBinder extends ValueTypePathBinder

trait ValueTypePathBinder {

  implicit def valueTypeBinder[T <: ValueType[_]](implicit parse: String => Try[T]): PathBindable[T] = new PathBindable[T] {

    def bind(key: String, value: String) = parse(value) match {
      case Success(valueType) => Right(valueType)
      case Failure(exception) => Left(s"Cannot bind '$value': ${exception.getMessage}")
    }

    def unbind(key: String, value: T) = value.value.toString
  }

}
