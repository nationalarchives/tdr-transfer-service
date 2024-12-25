import sbt.*

object Dependencies {
  private val http4sVersion = "0.23.30"
  private val mockitoVersion = "1.17.37"
  private val pureConfigVersion = "0.17.8"
  private val tapirVersion = "1.11.11"

  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.224"

  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.7"

  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.400"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.199"

  lazy val http4sCirce = "org.http4s" %% "http4s-circe" % http4sVersion
  lazy val http4sEmberServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
  lazy val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion

  lazy val keycloakMock = "com.tngtech.keycloakmock" % "mock" % "0.17.0"

  lazy val logBackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "8.0"
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.5.15"

  lazy val metadataSchema = "uk.gov.nationalarchives" % "da-metadata-schema_3" % "0.0.42"
  lazy val mockito = "org.mockito" %% "mockito-scala" % mockitoVersion
  lazy val mockitoScalaTest = "org.mockito" %% "mockito-scala-scalatest" % mockitoVersion

  lazy val pekkoTestKitHttp = "org.apache.pekko" %% "pekko-http-testkit" % "1.1.0"
  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % pureConfigVersion
  lazy val pureConfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion

  lazy val tapirHttp4sServer = "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion
  lazy val tapirJsonCirce = "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion
  lazy val tapirSwaggerUI = "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"
}
