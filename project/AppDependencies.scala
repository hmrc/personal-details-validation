import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test()

  private val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-play-25" % "1.0.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.1.0",
    ws
  )

  private def test(scope: String = "test,it") = Seq(
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope
  )
}
