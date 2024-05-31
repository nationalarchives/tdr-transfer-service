import Dependencies.*

ThisBuild / version      := "0.0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "uk.gov.nationalarchives"

lazy val root = (project in file("."))
  .settings(
    name := "tdr-transfer-service",
    libraryDependencies ++= Seq(
      catsEffect,
      http4sDsl,
      http4sEmberServer
    )
  )

(Compile / run / mainClass) := Some("uk.gov.nationalarchives.tdr.transfer.service.TransferServiceServer")

(assembly / assemblyJarName) := "transferservice.jar"

(assembly / assemblyMergeStrategy) := {
  case PathList("META-INF", x, xs @ _*) if x.toLowerCase == "services" => MergeStrategy.filterDistinctLines
  case PathList("META-INF", xs @ _*)                                   => MergeStrategy.discard
  case PathList("reference.conf")                                      => MergeStrategy.concat
  case _                                                               => MergeStrategy.first
}

(assembly / mainClass) := Some("uk.gov.nationalarchives.tdr.transfer.service.TransferServiceServer")
