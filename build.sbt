import TestPhases.oneForkedJvmPerTest
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "personal-details-validation"

lazy val playSettings: Seq[Setting[_]] = Seq(
  routesImport ++= Seq(
    "uk.gov.hmrc.personaldetailsvalidation.model.ValidationId",
    "uk.gov.hmrc.play.pathbinders.PathBinders._"
  )
)

lazy val scoverageSettings: Seq[Def.Setting[_ >: String with Double with Boolean]] = {
  import scoverage._

  Seq(
    ScoverageKeys.coverageExcludedPackages :=
      """<empty>;
        |Reverse.*;
        |.*BuildInfo.*;
        |.*config.*;
        |.*Routes.*;
        |.*RoutesPrefix.*;""".stripMargin,

    ScoverageKeys.coverageMinimum := 80,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory): _*)
  .settings(majorVersion := 0)
  .settings(scalaSettings: _*)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    scalaVersion := "2.12.12",
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(base / "it")).value,
    unmanagedResourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(base / "it" / "resources")).value,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false
  )
  .settings(resolvers ++= Seq(
    Resolver.bintrayRepo("hmrc", "releases"),
    Resolver.jcenterRepo
  ))
