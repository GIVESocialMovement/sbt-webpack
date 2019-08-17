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
