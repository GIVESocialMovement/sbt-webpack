package givers.webpack

import java.io.File
import java.nio.file.Files

import helpers.BaseSpec
import sbt.internal.util.ManagedLogger
import sbt.{Tests => _, _}
import utest._

object CompilerIntegrationSpec extends BaseSpec {

  val tests = Tests {
    'compile - {
      "run webpack and get result correctly" - {
        val targetDir = Files.createTempDirectory("sbt-webpack-compiler-integration-spec").toFile
        val baseDir = new File("src") / "test" / "scala" / "givers" / "webpack" / "assets"
        val compiler = new Compiler(
          binary = if (sys.props.getOrElse("os.name", "").toLowerCase.contains("win")) {
            new File("node_modules") / ".bin" / "webpack.cmd" // Detect Windows
          } else {
            new File("node_modules") / ".bin" / "webpack"
          },
          configFile = new File("src") / "test" / "scala" / "givers" / "webpack" / "assets" / "webpack.config.js",
          baseDir = baseDir,
          targetDir = targetDir,
          isProd = true,
          logger = mock[ManagedLogger],
          nodeModules =  new File("node_modules")
        )

        val baseInputDir = new File("src") / "test" / "scala" / "givers" / "webpack" / "assets" / "javascripts"
        val aJs = baseInputDir / "a.js"
        val bJs = baseInputDir / "b.js"
        val cJs = baseInputDir / "common" / "c.js"

        val output1 = targetDir / "dist" / "compiled.js"
        val output2 = targetDir / "dist" / "another-compiled.js"

        val inputs = Seq(aJs, bJs)
        val entries = Map(
          targetDir.relativize(output1).get.toString -> inputs.map { i => baseDir.relativize(i).get.toString },
          targetDir.relativize(output2).get.toString -> Seq(baseDir.relativize(bJs).get.toString)
        )
        val result = compiler.compile(entries, inputs.map(_.toPath))

        result.success ==> true
        result.entries.size ==> 2

        result.entries.head.inputFile ==> aJs
        result.entries.head.filesWritten.size ==> 1
        Files.exists(result.entries.head.filesWritten.head) ==> true
        result.entries.head.filesWritten.head ==> output1.toPath
        result.entries.head.filesRead ==> Set(aJs.toPath, cJs.toPath)
        // If CSS dependency is tracked properly, the below should have been true.
        // See more: https://github.com/GIVESocialMovement/sbt-vuefy/issues/20
        //        result.entries.head.filesRead ==> inputs.map(_.toPath).toSet ++ Set(baseInputDir / "dependencies" / "style.scss")

        result.entries(1).inputFile ==> bJs
        result.entries(1).filesWritten.size ==> 2
        result.entries(1).filesWritten.forall { f => Files.exists(f) } ==> true
        result.entries(1).filesWritten ==> Set(output1.toPath, output2.toPath)
        result.entries(1).filesRead ==> Set(bJs.toPath)
      }
    }
  }
}
