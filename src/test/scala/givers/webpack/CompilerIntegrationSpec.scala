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
          logger = mock[ManagedLogger],
          nodeModules =  new File("node_modules")
        )

        val baseInputStylesheetDir = new File("src") / "test" / "scala" / "givers" / "webpack" / "assets" / "stylesheets"
        val baseInputJsDir = new File("src") / "test" / "scala" / "givers" / "webpack" / "assets" / "javascripts"
        val aJs = baseInputJsDir / "a.js"
        val bJs = baseInputJsDir / "b.js"
        val cJs = baseInputJsDir / "common" / "c.js"

        val output1 = targetDir / "dist" / "compiled.js"
        val output2 = targetDir / "dist" / "another-compiled.js"

        val inputs = Seq(aJs, bJs)
        val entries = Map(
          targetDir.relativize(output1).get.toString -> inputs.map { i => baseDir.relativize(i).get.toString },
          targetDir.relativize(output2).get.toString -> Seq(baseDir.relativize(bJs).get.toString)
        )
        val result = compiler.compile(entries, inputs.map(_.toPath))

        result.success ==> true
        result.entries.size ==> 8

        // api.js is a file in css-loader and involved in generating compiled.js.
        // It makes sense to let sbt-web track the file.
        // This means, if css-loader is updated, compiled.js will be re-compiled, which is amazing!
        result.entries.head.inputFile.getCanonicalPath ==> (new File(".") / "node_modules" / "css-loader" / "dist" / "runtime" / "api.js").getCanonicalPath
        result.entries.head.filesWritten ==> Set(
          output1.toPath,
          output1.toPath.getParent.resolve(s"${output1.name}.map")
        )

        result.entries(1).inputFile.getCanonicalPath ==> aJs.getCanonicalPath
        result.entries(1).filesWritten.forall { f => Files.exists(f) } ==> true
        result.entries(1).filesWritten ==> Set(output1.toPath, output1.toPath.getParent.resolve(s"${output1.name}.map"))

        result.entries(2).inputFile.getCanonicalPath ==> bJs.getCanonicalPath
        result.entries(2).filesWritten.forall { f => Files.exists(f) } ==> true
        result.entries(2).filesWritten ==> Set(
          output1.toPath,
          output1.toPath.getParent.resolve(s"${output1.name}.map"),
          output2.toPath,
          output2.toPath.getParent.resolve(s"${output2.name}.map"),
        )

        result.entries(3).inputFile.getCanonicalPath ==> cJs.getCanonicalPath
        result.entries(3).filesWritten.forall { f => Files.exists(f) } ==> true
        result.entries(3).filesWritten ==> Set(output1.toPath, output1.toPath.getParent.resolve(s"${output1.name}.map"))

        // The below is the code-splitting defined in assets/webpack.config.js
        result.entries(4).inputFile.getCanonicalPath ==> (baseInputJsDir / "vendor" / "lib.js").getCanonicalPath
        result.entries(4).filesWritten.size ==> 2
        result.entries(4).filesWritten.forall { f => Files.exists(f) } ==> true
        result.entries(4).filesWritten ==> Set(
          (targetDir / "dist" / "vendor.js").toPath,
          (targetDir / "dist" / "vendor.js.map").toPath,
        )

        result.entries(5).inputFile.getCanonicalPath ==> (baseInputStylesheetDir / "dep.scss").getCanonicalPath
        result.entries(5).filesWritten ==> Set(
          output1.toPath,
          output1.toPath.getParent.resolve(s"${output1.name}.map"),
        )

        result.entries(6).inputFile.getCanonicalPath ==> (baseInputStylesheetDir / "sass.scss").getCanonicalPath
        result.entries(6).filesWritten ==> Set(
          output1.toPath,
          output1.toPath.getParent.resolve(s"${output1.name}.map"),
        )

        result.entries(7).inputFile.getCanonicalPath ==> (baseInputStylesheetDir / "test.css").getCanonicalPath
        result.entries(7).filesWritten ==> Set(
          output1.toPath,
          output1.toPath.getParent.resolve(s"${output1.name}.map"),
        )
      }
    }
  }
}
