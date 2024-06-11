import sbt.*

object Dependencies {
  private val http4sVersion = "0.23.26"
  private val pureConfigVersion = "0.17.6"

  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.4"

  lazy val http4sEmberServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
  lazy val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion

  lazy val logBackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.4"
  lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.5.6"

  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % pureConfigVersion
  lazy val pureConfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18"
}
