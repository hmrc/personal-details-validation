import sbt.Tests.{Group, SubProcess}
import sbt.{ForkOptions, TestDefinition}

private object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] =
    tests map {
      test =>
        Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq(
          s"-Dtest.name=${test.name}",
          "-Dlogger.resource=logback-it-test.xml"
        ))))
    }
}
