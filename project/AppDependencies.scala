import play.core.PlayVersion
import sbt._

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test
  
  private val compile = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "1.2.0",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % "7.15.0",
    "uk.gov.hmrc"       %% "domain"                    % "8.3.0-play-28",
    "uk.gov.hmrc"       %% "reactive-circuit-breaker"  % "4.1.0",
    "org.typelevel"     %% "cats-core"                 % "2.9.0"
  )

  private val test = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % "1.2.0"         % "test,it",
    "uk.gov.hmrc"             %% "service-integration-test"   % "1.3.0-play-28" % "test,it",
    "org.scalamock"           %% "scalamock"                  % "5.2.0"         % "test,it",
    "org.scalatestplus"       %% "scalacheck-1-15"            % "3.2.11.0"      % Test,
    "org.scalacheck"          %% "scalacheck"                 % "1.17.0"        % Test,
    "org.pegdown"             %  "pegdown"                    % "1.6.0"         % "test,it",
    "com.github.tomakehurst"  %  "wiremock-jre8"              % "2.35.0"        % "test,it",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.36.8"        % Test,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"  % "2.14.2"        % "test,it",
  )
}
