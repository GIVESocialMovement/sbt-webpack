name := """test-play-project"""
organization := "io.github.givesocialmovement"

version := "1.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb, SbtWebpack)

scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  guice,
  "com.google.inject" % "guice" % "5.1.0"
)

Assets / WebpackKeys.webpack / WebpackKeys.binary := {
  // Detect windows
  if (sys.props.getOrElse("os.name", "").toLowerCase.contains("win")) {
    new File(".") / "node_modules" / ".bin" / "webpack.cmd"
  } else {
    new File(".") / "node_modules" / "webpack" / "bin" / "webpack.js"
  }
}
Assets / WebpackKeys.webpack / WebpackKeys.configFile := new File(".") / "webpack.config.js"
Assets / WebpackKeys.webpack / includeFilter := "*.js" || "*.vue"
Assets / WebpackKeys.webpack / WebpackKeys.entries := Map(
  "javascripts/compiled-a.js" -> Seq("app/assets/javascripts/a.js"),
  "javascripts/test.js" -> Seq("app/assets/javascripts/test.vue"),
  "javascripts/compiled-b.js" -> Seq(
    "app/assets/javascripts/b.js",
    "node_modules/vue/dist/vue.runtime.js",
    "node_modules/axios/dist/axios.js",
    "node_modules/vue-i18n/dist/vue-i18n.js",
  )
)
