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

package uk.gov.hmrc.json

import play.api.libs.json._

object ReadOps {
  implicit class JsPathOps(path: JsPath) extends JsPath {
    def readOrError[T](error: => String)(implicit r: Reads[T]): Reads[T] = new Reads[T] {
      override def reads(json: JsValue): JsResult[T] = path.readNullable.reads(json) match {
        case JsSuccess(Some(value), _) => JsSuccess(value, path)
        case JsSuccess(None, _) => JsError(error)
        case err@JsError(_) => err
      }
    }
  }
}
