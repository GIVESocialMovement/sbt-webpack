lazy val `sbt-webpack` = project in file(".")

enablePlugins(SbtWebBase)

organization := "givers.webpack"
name := "sbt-webpack"
version in ThisBuild := "0.8.0"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.6.13",
  "org.mockito" % "mockito-core" % "2.18.3" % Test,
  "com.lihaoyi" %% "utest" % "0.6.3" % Test
)

testFrameworks += new TestFramework("utest.runner.Framework")

publishMavenStyle := true

bintrayOrganization := Some("givers")

bintrayRepository := "maven"

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT")))

scmInfo := Some(ScmInfo(
  browseUrl = url("https://github.com/GIVESocialMovement/sbt-webpack"),
  connection = "scm:git:git@github.com:GIVESocialMovement/sbt-webpack.git",
  devConnection = Some("scm:git:git@github.com:GIVESocialMovement/sbt-webpack.git")
))

addSbtJsEngine("1.2.3")
