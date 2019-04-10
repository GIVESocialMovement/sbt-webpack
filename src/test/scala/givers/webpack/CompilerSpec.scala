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
            inputs.head.name -> Set(inputs.head.name),
            inputs(1).name -> Set(inputs.head.name, inputs(1).name)
          )
        )

        val result = compiler.compile(rawEntries, inputs.map(_.path))
        result.success ==> true
        result.entries.size ==> 2

        result.entries.head.inputFile.getCanonicalPath ==> inputs.head.path.toFile.getCanonicalPath
        result.entries.head.filesRead ==> Set(inputs.head.path)
        result.entries.head.filesWritten ==> Set(output.toPath)

        result.entries(1).inputFile.getCanonicalPath ==> inputs(1).path.toFile.getCanonicalPath
        result.entries(1).filesRead ==> Set(inputs.head.path, inputs(1).path)
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
      def make(s: String) = s"vue${Path.sep}$s"
      val a = make("a")
      val b = make("b")
      val c = make("c")
      val d = make("d")
      val nonVue = "non-vue"

      "builds correctly with flatten" - {
        // Even on window, the path separator from webpack's command is still `/`.
        val jsonStr = JsArray(Seq(
          Json.obj(
            "name" -> "./vue/a",
            "reasons" -> Seq.empty[String]
          ),
          Json.obj(
            "name" -> "./vue/b",
            "reasons" -> Seq("./vue/a + 4 modules")
          ),
          Json.obj(
            "name" -> "./vue/c",
            "reasons" -> Seq("./vue/b + 4 modules")
          ),
          Json.obj(
            "name" -> "./vue/d",
            "reasons" -> Seq("./vue/a + 4 modules")
          )
        )).toString

        compute(jsonStr) ==> Map(
          a -> Set(a, b, c, d),
          b -> Set(b, c),
          c -> Set(c),
          d -> Set(d)
        )
      }

      "handles non ./vue correctly" - {
        val jsonStr = JsArray(Seq(
          Json.obj(
            "name" -> "./vue/a",
            "reasons" -> Seq.empty[String]
          ),
          Json.obj(
            "name" -> nonVue,
            "reasons" -> Seq("./vue/a + 4 modules")
          ),
          Json.obj(
            "name" -> "./vue/c + 4 modules",
            "reasons" -> Seq(nonVue)
          )
        )).toString

        compute(jsonStr) ==> Map(
          a -> Set(a, c),
          c -> Set(c)
        )
      }

      "handles cyclic dependencies" - {
        val jsonStr = JsArray(Seq(
          Json.obj(
            "name" -> "./vue/a",
            "reasons" -> Seq("./vue/c + 4 modules")
          ),
          Json.obj(
            "name" -> "./vue/b",
            "reasons" -> Seq("./vue/a + 4 modules")
          ),
          Json.obj(
            "name" -> "./vue/c",
            "reasons" -> Seq("./vue/b + 4 modules")
          ),
        )).toString()

        compute(jsonStr) ==> Map(
          a -> Set(a, b, c),
          b -> Set(a, b, c),
          c -> Set(a, b, c)
        )
      }
    }
  }
}