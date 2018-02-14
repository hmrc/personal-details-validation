import sbt.{ForkOptions, TestDefinition}
import sbt.Tests.{Group, SubProcess}

private object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
    tests map {
      test => Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq(
        s"-Dtest.name=${test.name}",
        "-Dlogger.resource=logback-it-test.xml"
      ))))
    }
}
