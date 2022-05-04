lazy val `sbt-webpack` = project in file(".")

enablePlugins(SbtWebBase)

organization := "io.github.givesocialmovement"
name := "sbt-webpack"
ThisBuild / version := "0.11.1"

scalaVersion := "2.12.15"

ThisBuild / versionScheme := Some("early-semver")

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "org.mockito" % "mockito-core" % "2.18.3" % Test,
  "com.lihaoyi" %% "utest" % "0.6.3" % Test
)

testFrameworks += new TestFramework("utest.runner.Framework")

publishMavenStyle := true

bintrayOrganization := Some("givers")

bintrayRepository := "maven"

Test / publishArtifact := false

pomIncludeRepository := { _ => false }

licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT")))

scmInfo := Some(ScmInfo(
  browseUrl = url("https://github.com/GIVESocialMovement/sbt-webpack"),
  connection = "scm:git:git@github.com:GIVESocialMovement/sbt-webpack.git",
  devConnection = Some("scm:git:git@github.com:GIVESocialMovement/sbt-webpack.git")
))

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

publishTo := sonatypePublishToBundle.value

addSbtJsEngine("1.2.3")
