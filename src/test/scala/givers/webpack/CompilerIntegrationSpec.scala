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

        val iterator = result.entries.sortBy(_.inputFile.getCanonicalPath).iterator

        // api.js is a file in css-loader and involved in generating compiled.js.
        // It makes sense to let sbt-web track the file.
        // This means, if css-loader is updated, compiled.js will be re-compiled, which is amazing!
        use(iterator.next()) { entry =>
          entry.inputFile.getCanonicalPath ==> (new File(".") / "node_modules" / "css-loader" / "dist" / "runtime" / "api.js").getCanonicalPath
          entry.filesWritten ==> Set(
            output1.getCanonicalFile,
            (output1.getParentFile / s"${output1.name}.map").getCanonicalFile
          )
        }

        use(iterator.next()) { entry =>
          entry.inputFile.getCanonicalPath ==> aJs.getCanonicalPath
          entry.filesWritten.forall { f => Files.exists(f.toPath) } ==> true
          entry.filesWritten ==> Set(
            output1.getCanonicalFile,
            (output1.getParentFile / s"${output1.name}.map").getCanonicalFile
          )
        }

        use(iterator.next()) { entry =>
          entry.inputFile.getCanonicalPath ==> bJs.getCanonicalPath
          entry.filesWritten.forall { f => Files.exists(f.toPath) } ==> true
          entry.filesWritten ==> Set(
            output1.getCanonicalFile,
            (output1.getParentFile / s"${output1.name}.map").getCanonicalFile,
            output2.getCanonicalFile,
            (output2.getParentFile / s"${output2.name}.map").getCanonicalFile,
          )
        }

        use(iterator.next()) { entry =>
          entry.inputFile.getCanonicalPath ==> cJs.getCanonicalPath
          entry.filesWritten.forall { f => Files.exists(f.toPath) } ==> true
          entry.filesWritten ==> Set(
            output1.getCanonicalFile,
            (output1.getParentFile / s"${output1.name}.map").getCanonicalFile
          )
        }

        use(iterator.next()) { entry =>
          // The below is the code-splitting defined in assets/webpack.config.js
          entry.inputFile.getCanonicalPath ==> (baseInputJsDir / "vendor" / "lib.js").getCanonicalPath
          entry.filesWritten.size ==> 2
          entry.filesWritten.forall { f => Files.exists(f.toPath) } ==> true
          entry.filesWritten ==> Set(
            (targetDir / "dist" / "vendor.js").getCanonicalFile,
            (targetDir / "dist" / "vendor.js.map").getCanonicalFile,
          )
        }

        use(iterator.next()) { entry =>
          entry.inputFile.getCanonicalPath ==> (baseInputStylesheetDir / "dep.scss").getCanonicalPath
          entry.filesWritten ==> Set(
            output1.getCanonicalFile,
            (output1.getParentFile / s"${output1.name}.map").getCanonicalFile,
          )
        }

        use(iterator.next()) { entry =>
          entry.inputFile.getCanonicalPath ==> (baseInputStylesheetDir / "sass.scss").getCanonicalPath
          entry.filesWritten ==> Set(
            output1.getCanonicalFile,
            (output1.getParentFile / s"${output1.name}.map").getCanonicalFile,
          )
        }

        use(iterator.next()) { entry =>
          entry.inputFile.getCanonicalPath ==> (baseInputStylesheetDir / "test.css").getCanonicalPath
          entry.filesWritten ==> Set(
            output1.getCanonicalFile,
            (output1.getParentFile / s"${output1.name}.map").getCanonicalFile,
          )
        }
      }
    }
  }
}
