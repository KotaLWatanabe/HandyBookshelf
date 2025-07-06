import Libraries.*

name := "HandyBookShelf"

ThisBuild / scalaVersion := "3.7.1"

ThisBuild / semanticdbEnabled := true

lazy val commonSettings = Seq(
  run / fork   := true,
  organization := "com.handybookshelf",
  version      := "0.1.0-SNAPSHOT",
  libraryDependencies ++= Libraries.common,
  Compile / compile / wartremoverErrors ++= Seq(
    Wart.ArrayEquals,
    Wart.AnyVal,
    Wart.Enumeration,
    Wart.ExplicitImplicitTypes,
    Wart.FinalCaseClass,
    Wart.FinalVal,
    Wart.JavaConversions,
    Wart.LeakingSealed,
    Wart.NonUnitStatements,
    Wart.Product,
    Wart.Return,
    Wart.Serializable,
    Wart.StringPlusAny
  ),
  wartremoverExcluded += sourceManaged.value,
  scalacOptions ++= Seq(
    "UTF-8",          // ファイルのエンコーディング
    "-deprecation",   // 非推奨APIに関する警告
    "-unchecked",     // 型チェックに関する警告
    "-feature",       // 言語仕様の変更に関する警告
    "-explain-types", // 型エラー時に詳細な説明を表示
//    "-Werror", // 警告をエラーとして扱う
//    "-Wunused:all",  // 未使用コードに関する警告
    "-source:future", // 未来のバージョンの言語仕様を使用
    "cats.effect.tracing.exceptions.enhanced=true",
    "cats.effect.tracing.mode=full",
    "cats.effect.tracing.buffer.size=64"
  ),
  scalacOptions --= Seq(
    "-Ykind-projector",
    "-P:kind-projector:underscore-placeholders"
  )
)

scalafixAll     := {}
scalafix        := {}
Test / scalafix := {}

//lazy val scalafix_input = (project in file("scalafix/input"))
//  .disablePlugins(ScalafixPlugin)
//  .settings(libraryDependencies ++= Seq(cats))
//lazy val scalafix_output = (project in file("scalafix/output"))
//  .disablePlugins(ScalafixPlugin)
//  .settings(libraryDependencies ++= Seq(cats))
//lazy val scalafix_rules = (project in file("scalafix/rules"))
//  .disablePlugins(ScalafixPlugin)
//  .settings(
//    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % _root_.scalafix.sbt.BuildInfo.scalafixVersion
//  )

def createTestSettings(
    _parallelExecution: Boolean = false,
    _limit: Int = 1,
    _fork: Boolean = false,
    _testForkedParallel: Boolean = false,
    _forkedTestGroup: Int = 1
) = Seq(
  Test / fork               := _fork,
  Test / parallelExecution  := _parallelExecution,
  Test / testForkedParallel := _testForkedParallel,
  concurrentRestrictions := Seq(
    Tags.limitAll(_limit),
    Tags.limit(Tags.ForkedTestGroup, _forkedTestGroup),
    Tags.exclusiveGroup(Tags.Clean)
  )
)

val testSettings = createTestSettings(
  _parallelExecution = true,
  _limit = 4,
  _fork = true,
  _testForkedParallel = true,
  _forkedTestGroup = 2
)

lazy val util = (project in file("util"))
  .enablePlugins(NativeImagePlugin)
//  .dependsOn(scalafix_rules % ScalafixConfig)
  .settings(
    commonSettings,
    nativeImageVersion := "24.0.0",
    nativeImageOptions ++= Seq("-H:+AllowIncompleteClasspath", "--no-fallback")
  )
  .settings(testSettings)

lazy val domain = (project in file("domain"))
  .settings(commonSettings)
  .dependsOn(util)

lazy val infrastructure = (project in file("infrastructure"))
  .settings(
    commonSettings,
    libraryDependencies ++= Libraries.cassandra ++ Libraries.dynamodb
  )
  .dependsOn(util, domain)

lazy val adopter = (project in file("adopter"))
  .settings(commonSettings)
  .dependsOn(util, domain, infrastructure)

lazy val usecase = (project in file("usecase"))
  .settings(commonSettings)
  .dependsOn(util, domain, adopter, infrastructure)

lazy val controller = (project in file("controller"))
  .settings(
    commonSettings,
    libraryDependencies ++= Libraries.pekko
  )
  .dependsOn(util, domain, infrastructure, adopter, usecase)
