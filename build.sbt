lazy val `sbt-webpack` = project in file(".")

enablePlugins(SbtWebBase)

organization := "io.github.givesocialmovement"
homepage := Some(url("https://github.com/GIVESocialMovement/sbt-webpack"))
developers := List(Developer("tanin", "tanin", "developers@giveasia.org", url("https://github.com/tanin47")))
name := "sbt-webpack"
version in ThisBuild := "0.10.0"

scalaVersion := "2.12.11"

publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "org.mockito" % "mockito-core" % "2.18.3" % Test,
  "com.lihaoyi" %% "utest" % "0.6.3" % Test
)

testFrameworks += new TestFramework("utest.runner.Framework")

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT")))

scmInfo := Some(ScmInfo(
  browseUrl = url("https://github.com/GIVESocialMovement/sbt-webpack"),
  connection = "scm:git:git@github.com:GIVESocialMovement/sbt-webpack.git",
  devConnection = Some("scm:git:git@github.com:GIVESocialMovement/sbt-webpack.git")
))

addSbtJsEngine("1.2.3")
