import sbt.*

object Dependencies {
  private val http4sVersion = "0.23.26"

  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.4"

  lazy val http4sEmberServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
  lazy val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18"
}
