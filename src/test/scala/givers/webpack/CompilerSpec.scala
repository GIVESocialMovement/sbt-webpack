package givers.webpack

import java.io.File
import java.nio.file.{Files, Paths}

import helpers.BaseSpec
import play.api.libs.json.{JsArray, Json}
import sbt.internal.util.ManagedLogger
import sbt.{Tests => _, _}
import utest._

import scala.io.Source

object CompilerSpec extends BaseSpec {

  val tests = Tests {
    'compile - {
      val logger = mock[ManagedLogger]
      val shell = mock[Shell]
      val computeDependencyTree = mock[ComputeDependencyTree]
      val computeEntryPoints = mock[ComputeEntryPoints]
      val prepareWebpackConfig = mock[PrepareWebpackConfig]
      val baseDir = new File("baseDir") / "somepath"
      val targetDir = new File("targetDir") / "anotherpath"
      val nodeModulesDir = new File("node_modules")
      val originalConfigFile = baseDir / "config" / "webpack.config.js"
      val webpackBinaryFile = baseDir / "binary" / "webpack.binary"
      val compiler = new Compiler(
        webpackBinaryFile,
        originalConfigFile,
        baseDir,
        targetDir,
        logger,
        nodeModulesDir,
        shell,
        prepareWebpackConfig,
        computeEntryPoints,
        computeDependencyTree,
      )

      val preparedConfigFile = new File("new") / "webpack" / "prepared-config.js"
      when(prepareWebpackConfig.apply(any(), any(), any())).thenReturn(preparedConfigFile.getAbsolutePath)

      "handles empty" - {
        when(computeEntryPoints.apply(any(), any(), any(), any())).thenReturn(Map.empty[File, Seq[Input]])

        compiler.compile(Map.empty, Seq.empty) ==> CompilationResult(true, Seq.empty)

        verify(computeEntryPoints).apply(baseDir, targetDir, Map.empty, Seq.empty)
        verifyZeroInteractions(shell, logger, computeDependencyTree, prepareWebpackConfig)
      }

      "fails" - {
        when(shell.execute(any(), any(), any())).thenReturn(1)

        val inputs = Seq(
          Input(Seq("a", "b", "c.js").mkString(Path.sep.toString), (baseDir / "a" / "b" / "c.js").toPath),
          Input(Seq("a", "b.js").mkString(Path.sep.toString), (baseDir / "a" / "b.js").toPath)
        )

        val output = targetDir / "javascripts" / "compiled.js"
        val entries = Map(output -> inputs)

        val rawEntries = entries
            .map { case (o, is) =>
              targetDir.relativize(o).get.toString -> is.map { input => baseDir.relativize(input.path.toFile).get.toString }
            }
        when(computeEntryPoints.apply(any(), any(), any(), any())).thenReturn(entries)

        val result = compiler.compile(rawEntries, Seq(inputs.head.path))
        result.success ==> false
        result.entries.isEmpty ==> true

        verify(prepareWebpackConfig).apply(
          originalWebpackConfig = originalConfigFile,
          entries = Map(output -> inputs),
          targetDir = targetDir
        )
        verify(computeEntryPoints).apply(baseDir, targetDir, rawEntries, Seq(inputs.head))
        verifyZeroInteractions(computeDependencyTree)
        verify(shell).execute(
          eq(
            Seq(
              webpackBinaryFile.getCanonicalPath,
              "--config", preparedConfigFile.getAbsolutePath,
            ).mkString(" ")),
          eq(baseDir),
          varArgsThat[(String, String)] { varargs =>
            varargs.size == 1 && varargs.head == ("NODE_PATH" -> nodeModulesDir.getCanonicalPath)
          }
        )
      }

      "compiles successfully" - {
        val inputs = Seq(
          Input(Seq("a", "b.js").mkString(Path.sep.toString), (baseDir / "a" / "b.js").toPath),
          Input(Seq("a", "b", "c.js").mkString(Path.sep.toString), (baseDir / "a" / "b" / "c.js").toPath),
        )

        val output = targetDir / "javascripts" / "compiled.js"
        val entries = Map(output -> inputs)

        val rawEntries = entries
          .map { case (o, is) =>
            targetDir.relativize(o).get.toString -> is.map { input => baseDir.relativize(input.path.toFile).get.toString }
          }

        when(computeEntryPoints.apply(any(), any(), any(), any())).thenReturn(entries)
        when(shell.execute(any(), any(), any())).thenReturn(0)
        when(computeDependencyTree.apply(any[File]())).thenReturn(
          Map(
            inputs.head.path.toFile.getCanonicalPath -> Set(targetDir.toPath.relativize(output.toPath).toString),
            inputs(1).path.toFile.getCanonicalPath -> Set(targetDir.toPath.relativize(output.toPath).toString)
          )
        )

        val result = compiler.compile(rawEntries, inputs.map(_.path))
        result.success ==> true
        result.entries.size ==> 2

        result.entries.head.inputFile.getCanonicalPath ==> inputs.head.path.toFile.getCanonicalPath
        result.entries.head.filesWritten ==> Set(output.toPath)

        result.entries(1).inputFile.getCanonicalPath ==> inputs(1).path.toFile.getCanonicalPath
        result.entries(1).filesWritten ==> Set(output.toPath)

        verify(prepareWebpackConfig).apply(
          originalWebpackConfig = originalConfigFile,
          entries = entries,
          targetDir = targetDir
        )
        verify(computeDependencyTree).apply(targetDir / "dependency-tree.json")
        verify(shell).execute(
          eq(Seq(
            webpackBinaryFile.getCanonicalPath,
            "--config", preparedConfigFile.getAbsolutePath,
          ).mkString(" ")),
          eq(baseDir),
          varArgsThat[(String, String)] { varargs =>
            varargs.size == 1 && varargs.head == ("NODE_PATH" -> nodeModulesDir.getCanonicalPath)
          }
        )
      }
    }

    'getWebpackConfig - {
      val originalWebpackConfig = Files.createTempFile("test", "test")
      val baseDir = new File("baseDir") / "somepath"
      val targetDir = new File("baseDir") / "anotherpath"

      val output1 = targetDir / "compiled.js"
      val (module1, file1) = Seq("a", "b", "c").mkString(Path.sep.toString) -> (baseDir / "a" / "b" / "c.js")

      val webpackConfig = (new PrepareWebpackConfig).apply(
        originalWebpackConfig = originalWebpackConfig.toFile,
        entries = Map(output1 -> Seq(Input(module1, file1.toPath))),
        targetDir = targetDir
      )
      val dependencyPluginFile = new File(webpackConfig).getParentFile / "dependency-plugin.js"

      // We don't test for the content because it's too complex. CompilerIntegrationSpec will cover the content.
      Files.exists(Paths.get(webpackConfig)) ==> true
      Files.exists(dependencyPluginFile.toPath) ==> true

      val src = Source.fromFile(dependencyPluginFile)
      src.mkString ==> Source.fromInputStream(getClass.getResourceAsStream("/dependency-plugin.js")).mkString
      src.close()

      Files.deleteIfExists(originalWebpackConfig)
      Files.deleteIfExists(dependencyPluginFile.toPath)
    }

    'buildDependencies - {
      val compute = new ComputeDependencyTree

      "builds correctly" - {
        // Even on window, the path separator from webpack's command is still `/`.
        // compilation.chunks give us flatten dependencies already.
        val jsonStr = JsArray(Seq(
          Json.obj(
            "output" -> "vue/a",
            "dependencies" -> Seq(
              "/full-path/vue/b",
              "/full-path/vue/c"
            )
          ),
          Json.obj(
            "output" -> "vue/b",
            "dependencies" -> Seq("/full-path/vue/c")
          ),
          Json.obj(
            "output" -> "vue/c",
            "dependencies" -> Seq.empty[String]
          )
        )).toString

        compute(jsonStr) ==> Map(
          "/full-path/vue/c" -> Set("vue/a", "vue/b"),
          "/full-path/vue/b" -> Set("vue/a")
        )
      }
    }
  }
}