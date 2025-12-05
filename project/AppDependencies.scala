import sbt.*

private object AppDependencies {

  def apply(): Seq[ModuleID] = compile ++ test

  val hmrcMongo = "2.11.0"
  val bootstrap = "10.4.0"
  
  private val compile = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongo,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrap,
    "uk.gov.hmrc"       %% "domain-play-30"            % "11.0.0",
    "uk.gov.hmrc"       %% "reactive-circuit-breaker"  % "6.1.0",
    "org.typelevel"     %% "cats-core"                 % "2.13.0",
    "uk.gov.hmrc"       %% "crypto-json-play-30"       % "8.4.0"
  )

  private val test = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongo,
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrap,
    "org.scalatestplus"       %% "scalacheck-1-18"            % "3.2.19.0",
  ).map(_ % "test")
}
