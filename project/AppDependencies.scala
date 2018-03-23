import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test()

  private val compile = Seq(
    "org.typelevel" %% "cats-core" % "1.0.1",
    "uk.gov.hmrc" %% "bootstrap-play-25" % "1.5.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.1.0",
    "uk.gov.hmrc" %% "domain" % "5.0.0",
    "uk.gov.hmrc" %% "valuetype" % "1.1.0",
    ws
  )

  private def test(scope: String = "test,it") = Seq(
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
    "org.scalamock" %% "scalamock" % "4.0.0" % "test",
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % "it",
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "3.0.0" % "test",
    "com.github.tomakehurst" % "wiremock" % "2.12.0" % "it"
  )
}
