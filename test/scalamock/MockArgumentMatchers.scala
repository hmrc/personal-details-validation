/*
 * Copyright 2022 HM Revenue & Customs
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

package scalamock

import org.scalamock.matchers.{MatcherBase, Matchers => ScalamockMatchers}
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.{Matchers => ScalatestMatchers}

import scala.reflect.ClassTag

trait MockArgumentMatchers extends ScalamockMatchers with ScalatestMatchers {
  self: MockFactory =>

  def instanceOf[T](implicit classTag: ClassTag[T]): MatcherBase = argAssert{x: T => x.getClass shouldBe classTag.runtimeClass}
}
