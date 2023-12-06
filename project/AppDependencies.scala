import play.core.PlayVersion
import sbt._

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test

  val hmrcMongo = "1.6.0"
  val bootstrap = "7.23.0"
  
  private val compile = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % hmrcMongo,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrap,
    "uk.gov.hmrc"       %% "domain"                    % "8.3.0-play-28",
    "uk.gov.hmrc"       %% "reactive-circuit-breaker"  % "4.1.0",
    "org.typelevel"     %% "cats-core"                 % "2.10.0",
    "uk.gov.hmrc"       %% "crypto-json-play-28"       % "7.6.0",
    "uk.gov.hmrc"       %% "crypto"                    % "7.6.0"
  )

  private val test = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % hmrcMongo       % "test,it",
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrap       % "test,it",
    "org.scalamock"           %% "scalamock"                  % "5.2.0"         % "test,it",
    "org.scalatestplus"       %% "scalacheck-1-15"            % "3.2.11.0"      % Test,
    "org.scalacheck"          %% "scalacheck"                 % "1.17.0"        % Test,
    "org.pegdown"             %  "pegdown"                    % "1.6.0"         % "test,it",
    "org.wiremock"            %  "wiremock-standalone"        % "3.3.1"         % "test, it",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.36.8"        % Test,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"  % "2.16.0"        % "test,it",
  )
}
