import Dependencies.*

ThisBuild / version      := "0.0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "uk.gov.nationalarchives"

lazy val root = (project in file("."))
  .settings(
    name := "tdr-transfer-service",
    libraryDependencies ++= Seq(
      authUtils,
      catsEffect,
      http4sCirce,
      http4sDsl,
      http4sEmberServer,
      keycloakMock % Test,
      logbackClassic,
      logBackEncoder,
      mockito % Test,
      pekkoTestKitHttp % Test,
      pureConfig,
      pureConfigCatsEffect,
      scalaTest % Test,
      tapirHttp4sServer,
      tapirJsonCirce,
      tapirSwaggerUI
    )
  )

(Compile / run / mainClass) := Some("uk.gov.nationalarchives.tdr.transfer.service.api.TransferServiceServer")

(assembly / assemblyJarName) := "transferservice.jar"

(assembly / assemblyMergeStrategy) := {
  case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
  case PathList("META-INF", "resources", "webjars", "swagger-ui", _*)               => MergeStrategy.singleOrError
  case PathList("META-INF", x, xs @ _*) if x.toLowerCase == "services"              => MergeStrategy.filterDistinctLines
  case PathList("META-INF", xs @ _*)                                                => MergeStrategy.discard
  case PathList("reference.conf")                                                   => MergeStrategy.concat
  case _                                                                            => MergeStrategy.first
}

(assembly / mainClass) := Some("uk.gov.nationalarchives.tdr.transfer.service.api.TransferServiceServer")
