package scalamock


import org.scalamock.matchers.{MatcherBase, Matchers => ScalamockMatchers}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers => ScalatestMatchers}

import scala.reflect.ClassTag

trait MockArgumentMatchers extends ScalamockMatchers with ScalatestMatchers {
  self: MockFactory =>

  def instanceOf[T](implicit classTag: ClassTag[T]): MatcherBase = argAssert{x: T => x.getClass shouldBe classTag.runtimeClass}
}
