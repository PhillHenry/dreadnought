import Dependencies._

ThisBuild / scalaVersion     := "3.1.1"
ThisBuild / version          := "0.1.3"
ThisBuild / organization     := "uk.co.odinconsultants"
ThisBuild / organizationName := "OdinConsultants"
ThisBuild / versionScheme    := Some("early-semver")

ThisBuild / evictionErrorLevel   := Level.Warn
ThisBuild / scalafixDependencies += Libraries.organizeImports

ThisBuild / resolvers += Resolver.sonatypeRepo("snapshots")

ThisBuild / organizationHomepage := Some(url("http://odinconsultants.co.uk/"))

ThisBuild / scmInfo     := Some(
  ScmInfo(
    url("https://github.com/PhillHenry/dreadnought"),
    "scm:git@github.com:PhillHenry/dreadnought",
  )
)
ThisBuild / developers  := List(
  Developer(
    id = "PhillipHenry",
    name = "PhillipHenry",
    email = "ph@odinconsultants.co.uk",
    url = url("http://odinconsultants.co.uk"),
  )
)

ThisBuild / description := "Handle Docker containers in Scala"
ThisBuild / licenses    := List(
  "Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")
)
ThisBuild / homepage    := Some(url("https://github.com/PhillHenry/dreadnought"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo            := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots".at(nexus + "content/repositories/snapshots"))
  else Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
}
ThisBuild / publishMavenStyle    := true

Compile / run / fork := true

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / semanticdbEnabled    := true // for metals

val commonSettings = List(
  scalacOptions ++= List("-source:future"),
  scalafmtOnCompile := false, // recommended in Scala 3
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  libraryDependencies ++= Seq(
    Libraries.cats,
    Libraries.catsEffect,
    Libraries.fs2Core,
    Libraries.fs2Kafka,
    Libraries.monocleCore.value,
    Libraries.ip4s,
    Libraries.logBack,
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
  .aggregate(lib, core, it, docker, examples)

lazy val lib = (project in file("modules/lib"))
  .settings(commonSettings ++ List(name := "dreadnought-lib"): _*)

lazy val core = (project in file("modules/core"))
  .settings(commonSettings ++ List(name := "dreadnought-core"): _*)
  .dependsOn(lib)

lazy val docker = (project in file("modules/docker"))
  .settings(commonSettings ++ List(name := "dreadnought-docker"): _*)
  .dependsOn(core)
  .settings(
    libraryDependencies ++= List(
      Libraries.dockerJava,
      Libraries.dockerJavaTransport,
    )
  )

lazy val examples = (project in file("modules/examples"))
  .settings(commonSettings ++ List(name := "dreadnought-examples"): _*)
  .dependsOn(docker)

// integration tests
lazy val it = (project in file("modules/it"))
  .settings(commonSettings ++ List(name := "dreadnought-it"): _*)
  .dependsOn(core)
  .settings(
    libraryDependencies ++= List(
      "ch.qos.logback" % "logback-classic" % "1.2.11" % Test
    )
  )

lazy val docs = project
  .in(file("mdocs"))
  .settings(
    mdocIn        := file("modules/docs"),
    mdocOut       := file("target/docs"),
    mdocVariables := Map("VERSION" -> version.value),
//    mdoc          := run.in(Compile).evaluated,
  )
  .settings(commonSettings)
  .dependsOn(examples)
  .enablePlugins(MdocPlugin)

addCommandAlias("runLinter", ";scalafixAll --rules OrganizeImports")
