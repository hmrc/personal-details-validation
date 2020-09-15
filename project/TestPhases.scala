import sbt.{ForkOptions, TestDefinition}
import sbt.Tests.{Group, SubProcess}

private object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] =
    tests map {
      test =>
        Group(test.name, Seq(test), SubProcess(ForkOptions().withRunJVMOptions(Vector(
          s"-Dtest.name=${test.name}",
          "-Dlogger.resource=logback-it-test.xml"
        ))))
    }
}
