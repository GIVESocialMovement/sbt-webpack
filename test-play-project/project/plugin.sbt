lazy val root = Project("plugins", file(".")).aggregate(sbtWebpack).dependsOn(sbtWebpack)

lazy val sbtWebpack = RootProject(file("./..").getCanonicalFile.toURI)

resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.2")
