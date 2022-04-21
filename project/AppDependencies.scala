import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test


  private val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % "5.16.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.62.0",
    "uk.gov.hmrc"       %% "domain"                    % "6.0.0-play-28",
    "org.typelevel"     %% "cats-core"                 % "2.0.0"
  )

  private val test = Seq(
    "com.typesafe.play" %% "play-test" % PlayVersion.current % "test,it",
    "com.github.tomakehurst" % "wiremock-jre8" % "2.28.0" % "test,it",

    "org.scalatest"          %% "scalatest"                   % "3.2.10"                % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"          % "5.1.0"                 % "test, it",
    "org.scalatestplus"      %% "scalacheck-1-15"             % "3.2.10.0"              % Test,
    "org.scalacheck"         %% "scalacheck"                  % "1.15.4"                % Test,
    "com.vladsch.flexmark"   %   "flexmark-all"               % "0.62.2"                % Test,

    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.12.3"  % "test, it",

    "com.itv" %% "scalapact-circe-0-9" % "2.3.16" % "it",
    "com.itv" %% "scalapact-http4s-0-18" % "2.3.16" % "it",
    "com.itv" %% "scalapact-scalatest" % "2.3.16" % "it",
    "org.pegdown" % "pegdown" % "1.6.0" % "test,it",

    "org.scalamock" %% "scalamock" % "5.2.0" % "test,it",

    "uk.gov.hmrc" %% "service-integration-test" % "1.1.0-play-28" % "test,it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.62.0" % "test,it"
  )
}
