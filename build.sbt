import Dependencies._

ThisBuild / scalaVersion     := "3.1.1"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "uk.co.odinconsultants"
ThisBuild / organizationName := "OdinConsultants"
ThisBuild / versionScheme    := Some("early-semver")

ThisBuild / evictionErrorLevel := Level.Warn
ThisBuild / scalafixDependencies += Libraries.organizeImports

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

Compile / run / fork           := true

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / semanticdbEnabled    := true // for metals

val commonSettings = List(
  scalacOptions ++= List("-source:future"),
  scalafmtOnCompile := false, // recommended in Scala 3
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  libraryDependencies ++= Seq(
    Libraries.cats,
    Libraries.testkit,
    Libraries.catsEffect,
    Libraries.cirisCore,
    Libraries.cirisRefined,
    Libraries.fs2Core,
    Libraries.fs2Kafka,
    Libraries.kittens,
    Libraries.monocleCore.value,
    Libraries.neutronCore,
    Libraries.odin,
    Libraries.refinedCore.value,
    Libraries.refinedCats.value,
    Libraries.ip4s,
    Libraries.logBack,
    Libraries.monocleLaw       % Test,
    Libraries.scalacheck       % Test,
    Libraries.weaverCats       % Test,
    Libraries.weaverDiscipline % Test,
    Libraries.weaverScalaCheck % Test,
  ),
)

def dockerSettings(name: String) = List(
  Docker / packageName := organizationName + "-" + name,
  dockerBaseImage      := "jdk17-curl:latest",
  dockerExposedPorts ++= List(8080),
  makeBatScripts       := Nil,
  dockerUpdateLatest   := true,
)

lazy val root = (project in file("."))
  .settings(
    name := "dreadnought"
  )
  .aggregate(lib, core, it)

lazy val lib = (project in file("modules/lib"))
  .settings(commonSettings: _*)

lazy val core = (project in file("modules/core"))
  .settings(commonSettings: _*)
  .dependsOn(lib)

lazy val docker = (project in file("modules/docker"))
  .settings(commonSettings: _*)
  .dependsOn(core)
  .settings(
    libraryDependencies ++= List(
      Libraries.dockerJava,
      Libraries.dockerJavaTransport,
    )
  )

lazy val examples = (project in file("modules/examples"))
  .settings(commonSettings: _*)
  .dependsOn(docker)

// integration tests
lazy val it = (project in file("modules/it"))
  .settings(commonSettings: _*)
  .dependsOn(core)
  .settings(
    libraryDependencies ++= List(
      "ch.qos.logback" % "logback-classic" % "1.2.11" % Test
    )
  )

lazy val docs = project
  .in(file("docs"))
  .settings(
    mdocIn        := file("modules/docs"),
    mdocOut       := file("target/docs"),
    mdocVariables := Map("VERSION" -> version.value),
  )
  .dependsOn(core)
  .enablePlugins(MdocPlugin)

addCommandAlias("runLinter", ";scalafixAll --rules OrganizeImports")
