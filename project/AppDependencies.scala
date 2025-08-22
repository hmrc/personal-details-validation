import sbt.*

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test

  val hmrcMongo = "2.7.0"
  val bootstrap = "10.1.0"
  
  private val compile = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongo,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrap,
    "uk.gov.hmrc"       %% "domain-play-30"            % "11.0.0",
    "uk.gov.hmrc"       %% "reactive-circuit-breaker"  % "5.0.0",
    "org.typelevel"     %% "cats-core"                 % "2.13.0",
    "uk.gov.hmrc"       %% "crypto-json-play-30"       % "8.3.0"
  )

  private val test = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongo,
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrap,
    "org.scalamock"           %% "scalamock"                  % "7.4.1",
    "org.scalatestplus"       %% "scalacheck-1-15"            % "3.2.11.0",
    "org.scalacheck"          %% "scalacheck"                 % "1.18.1",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.64.8",
    "org.mockito"             %% "mockito-scala-scalatest"    % "2.0.0"
  ).map(_ % "test")
}
