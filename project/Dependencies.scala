import sbt.*

object Dependencies {
  private val http4sVersion = "0.23.26"
  private val pureConfigVersion = "0.17.6"
  private val tapirVersion = "1.10.7"
  private val tdrAWSUtilsVersion = "0.1.194"

  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.206-SNAPSHOT"

  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.4"

  lazy val http4sCirce = "org.http4s" %% "http4s-circe" % http4sVersion
  lazy val http4sEmberServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
  lazy val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion

  lazy val keycloakMock = "com.tngtech.keycloakmock" % "mock" % "0.16.0"

  lazy val logBackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.4"
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.5.6"

  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.31"
  lazy val mockitoScalaTest = "org.mockito" %% "mockito-scala-scalatest" % "1.17.31"

  lazy val pekkoTestKitHttp = "org.apache.pekko" %% "pekko-http-testkit" % "1.0.1"
  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % pureConfigVersion
  lazy val pureConfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion

  lazy val sfnUtils = "uk.gov.nationalarchives" %% "stepfunction-utils" % tdrAWSUtilsVersion
  lazy val snsUtils = "uk.gov.nationalarchives" %% "sns-utils" % tdrAWSUtilsVersion

  lazy val tapirHttp4sServer = "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion
  lazy val tapirJsonCirce = "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion
  lazy val tapirSwaggerUI = "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18"
}
