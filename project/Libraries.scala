import sbt.*

object Libraries {
  val catsVersion            = "2.13.0"
  val catsEffectVersion      = "3.6.1"
  val scalaTestVersion       = "3.2.19"
  val http4sVersion          = "0.23.30"
  val circeVersion           = "0.14.14"
  val munitVersion           = "1.1.1"
  val logbackVersion         = "1.5.18"
  val munitCatsEffectVersion = "1.0.7"
  val ulidVersion            = "2025.1.14"
  val ironVersion            = "3.0.2"
  val xmlVersion             = "2.4.0"
  val tapirVersion           = "1.11.34"
  val atnosEffVersion        = "8.0.0"
  val pekkoVersion           = "1.1.4"
  val cassandraDriverVersion = "4.17.0"
  val awsVersion             = "2.29.40"
  val fs2Version             = "3.11.0"

  // Libraries
  lazy val cats       = "org.typelevel" %% "cats-core"   % catsVersion
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % catsEffectVersion
  lazy val scalaTest =
    "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.18.1" % Test
  lazy val scalaTestCheck =
    "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % Test
  lazy val http4s: Seq[ModuleID] = Seq(
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-ember-client" % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion,
    "org.http4s" %% "http4s-dsl"          % http4sVersion
  )
  lazy val munit = "org.scalameta" %% "munit" % munitVersion % Test
  lazy val munitCatsEffect =
    "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectVersion % Test
  lazy val logback =
    "ch.qos.logback" % "logback-classic" % logbackVersion
  lazy val slf4j = "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.25.0"

  lazy val ulid =
    "org.wvlet.airframe" %% "airframe-ulid" % ulidVersion
  lazy val circe = "io.circe" %% "circe-generic" % circeVersion

  lazy val iron     = "io.github.iltotore"     %% "iron"      % ironVersion
//  lazy val ironCats = "io.github.iltotore"     %% "iron-cats" % ironVersion
  lazy val refined  = "eu.timepit"             %% "refined"   % "0.11.3"
  lazy val xml      = "org.scala-lang.modules" %% "scala-xml" % xmlVersion
  lazy val tapir: Seq[ModuleID] = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-core"              % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"     % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-client"     % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"      % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion
  )
  lazy val sttp: Seq[ModuleID] = Seq(
    "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.9"
  )
  lazy val atnosEff = "org.atnos" %% "eff" % atnosEffVersion

  lazy val pekko: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-actor-typed"         % pekkoVersion,
    "org.apache.pekko" %% "pekko-stream"              % pekkoVersion,
    "org.apache.pekko" %% "pekko-slf4j"               % pekkoVersion,
    "org.apache.pekko" %% "pekko-persistence-typed"   % pekkoVersion,
    "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test,
    "org.apache.pekko" %% "pekko-persistence-testkit" % pekkoVersion % Test
  )

  lazy val cassandra: Seq[ModuleID] = Seq(
    "com.datastax.oss" % "java-driver-core"          % cassandraDriverVersion,
    "com.datastax.oss" % "java-driver-query-builder" % cassandraDriverVersion
  )

  lazy val dynamodb: Seq[ModuleID] = Seq(
    "software.amazon.awssdk" % "dynamodb"       % awsVersion,
    "software.amazon.awssdk" % "url-connection-client" % awsVersion
  )

  lazy val fs2: Seq[ModuleID] = Seq(
    "co.fs2" %% "fs2-core" % fs2Version,
    "co.fs2" %% "fs2-io"   % fs2Version
  )

  // Projects
  val common: Seq[ModuleID] =
    Seq(
      cats,
      scalaTest,
      scalaCheck,
      scalaTestCheck,
      ulid,
      catsEffect,
      iron,
      refined,
      xml,
      slf4j,
      atnosEff
    ) ++ http4s ++ tapir ++ sttp ++ pekko ++ fs2
  val chapter1: Seq[ModuleID] = Seq(catsEffect)
}
