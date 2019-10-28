import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test()

  private val compile = Seq(
    "org.typelevel" %% "cats-core" % "1.0.1",
    "uk.gov.hmrc" %% "bootstrap-play-25" % "4.11.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.20.0-play-25",
    "uk.gov.hmrc" %% "domain" % "5.6.0-play-25",
    "uk.gov.hmrc" %% "valuetype" % "1.1.0",
    ws
  )

  private def test(scope: String = "test,it") = Seq(
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
    "org.scalamock" %% "scalamock" % "4.0.0" % "test",
    "com.itv" %% "scalapact-circe-0-9" % "2.2.5" % "test, it",
    "com.itv" %% "scalapact-http4s-0-16-2" % "2.2.5" % "test, it",
    "com.itv" %% "scalapact-scalatest" % "2.2.5" % "test, it",
    "org.scalatest" %% "scalatest" % "3.0.0" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % "it",
    "uk.gov.hmrc" %% "service-integration-test" % "0.9.0-play-25" % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "4.13.0-play-25" % "test",
    "com.github.tomakehurst" % "wiremock" % "2.12.0" % "it"
  )
}
