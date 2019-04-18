name := """test-play-project"""
organization := "givers.webpack"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb, SbtWebpack)

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(guice)

Assets / WebpackKeys.webpack / WebpackKeys.binary := {
  // Detect windows
  if (sys.props.getOrElse("os.name", "").toLowerCase.contains("win")) {
    new File(".") / "node_modules" / ".bin" / "webpack.cmd"
  } else {
    new File(".") / "node_modules" / ".bin" / "webpack"
  }
}
Assets / WebpackKeys.webpack / WebpackKeys.configFile := new File(".") / "webpack.config.js"
Assets / WebpackKeys.webpack / WebpackKeys.entries := Map("javascripts/compiled.js" -> Seq("app/assets/javascripts/a.js"))
