
import sbt._

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test

  val hmrcMongo = "2.6.0"
  val bootstrap = "8.6.0"
  
  private val compile = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongo,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrap,
    "uk.gov.hmrc"       %% "domain-play-30"            % "11.0.0",
    "uk.gov.hmrc"       %% "reactive-circuit-breaker"  % "5.0.0",
    "org.typelevel"     %% "cats-core"                 % "2.12.0",
    "uk.gov.hmrc"       %% "crypto-json-play-30"       % "7.6.0"
  )

  private val test = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongo       % Test,
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrap       % Test,
    "org.scalamock"           %% "scalamock"                  % "5.2.0"         % Test,
    "org.scalatestplus"       %% "scalacheck-1-15"            % "3.2.11.0"      % Test,
    "org.scalacheck"          %% "scalacheck"                 % "1.18.1"        % Test,
    "org.pegdown"             %  "pegdown"                    % "1.6.0"         % Test,
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.64.8"        % Test
  )
}
