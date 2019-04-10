package givers.webpack

import java.io.{File, FileOutputStream, PrintWriter}
import java.nio.file.{Files, Path}

import play.api.libs.json._
import sbt.internal.util.ManagedLogger

import scala.io.Source

case class CompilationResult(success: Boolean, entries: Seq[CompilationEntry])
case class CompilationEntry(inputFile: File, filesRead: Set[Path], filesWritten: Set[Path])
case class Input(name: String, path: Path)

class Shell {
  def execute(cmd: String, cwd: File, envs: (String, String)*): Int = {
    import scala.sys.process._

    Process(cmd, cwd, envs:_*).!
  }
}

class ComputeDependencyTree {
  val LOCAL_PATH_PREFIX_REGEX = "^\\./".r

  def sanitize(s: String): String = {
    LOCAL_PATH_PREFIX_REGEX.replaceAllIn(s, "").replaceAllLiterally("/", sbt.Path.sep.toString)
  }

  def apply(file: File): Map[String, Set[String]] = {
    apply(scala.io.Source.fromFile(file).mkString)
  }

  def apply(content: String): Map[String, Set[String]] = {
    val json = Json.parse(content)

    val deps = json.as[JsArray].value
      .flatMap { obj =>
        // For some reason, webpack or vue-loader includes the string ` + 4 modules` in `name`.
        val name = obj("name").as[String].split(" \\+ ").head
        val relations = obj("reasons").as[Seq[JsValue]]
          .flatMap {
            case JsNull => None
            // For some reason, webpack or vue-loader includes the string ` + 4 modules` in `moduleName`.
            // See: https://github.com/webpack/webpack/issues/8507
            case JsString(v) => Some(v.split(" \\+ ").head)
            case _ => throw new IllegalArgumentException()
          }
          .map { reason =>
            reason -> name
          }

        relations ++ Seq(name -> name) // the file also depends on itself.
      }
      .groupBy { case (key, _) => key }
      .mapValues(_.map(_._2).toSet)

    flatten(deps)
      // We only care about our directories.
      // The path separator here is always `/`, even on windows.
      .filter { case (key, _) => key.startsWith("./") }
      .mapValues { vs =>
        vs.filter { v =>
          // There are some dependencies that we don't care about.
          // An example: ./vue/component-a.vue?vue&type=style&index=0&id=f8aaa26e&scoped=true&lang=scss&
          // Another example: /home/tanin/projects/sbt-vuefy/node_modules/vue-style-loader!/home/ta...
          v.startsWith("./") && !v.contains("?")
        }
      }
      .map { case (key, values) =>
        sanitize(key) -> values.map(sanitize)
      }
  }

  private[this] def flatten(deps: Map[String, Set[String]]): Map[String, Set[String]] = {
    var changed = false
    val newDeps = deps
      .map { case (key, children) =>
        val newChildren = children ++ children.flatMap { v => deps.getOrElse(v, Set.empty) }
        if (newChildren.size != children.size) {
          changed = true
        }
        key -> newChildren
      }

    if (changed) {
      flatten(newDeps)
    } else {
      newDeps
    }
  }
}

class PrepareWebpackConfig {
  def apply(originalWebpackConfig: File, entries: Map[File, Seq[Input]], targetDir: File) = {
    import sbt._

    val tmpDir = Files.createTempDirectory("sbt-webpack")
    val targetFile = tmpDir.toFile / "webpack.config.js"

    Files.copy(originalWebpackConfig.toPath, targetFile.toPath)

    val webpackConfigFile = new PrintWriter(new FileOutputStream(targetFile, true))
    try {
      val js = JsObject(
        entries
          .map { case (output, inputs) =>
            targetDir.relativize(output).get.toString -> JsArray(inputs.map { i => JsString(i.path.toFile.getCanonicalPath) })
          }
          .toSeq
      )
      webpackConfigFile.write("\n")
      webpackConfigFile.write(s"module.exports.entry = ${Json.prettyPrint(js)};")
      webpackConfigFile.write(
        s"""
          |module.exports.output = module.exports.output || {};
          |module.exports.output.filename = module.exports.output.filename || '[name]';
          |module.exports.output.path = '${targetDir.getCanonicalPath}';
          |
          |const DependencyPlugin = require('./dependency-plugin.js');
          |module.exports.plugins = module.exports.plugins || [];
          |module.exports.plugins.push(new DependencyPlugin());
        """.stripMargin)
    } finally {
      webpackConfigFile.close()
    }

    val dependencyPluginFile = new PrintWriter(tmpDir.toFile / "dependency-plugin.js")
    try {
      dependencyPluginFile.write(Source.fromInputStream(getClass.getResourceAsStream("/dependency-plugin.js")).mkString)
    } finally {
      dependencyPluginFile.close()
    }

    targetFile.getAbsolutePath
  }
}


class ComputeEntryPoints {
  def apply(
    baseDir: File,
    targetDir: File,
    entryPoints: Map[String, Seq[String]],
    inputs: Seq[Input]
  ): Map[File, Seq[Input]] = {
    import sbt._

    val modified = inputs.map(_.name).toSet
    val filtered = entryPoints
      .filter { case (_, files) =>
        files.exists { f => modified.contains(f) }
      }

    filtered
      .map { case (output, is) =>
        (targetDir / output) -> is.map { i => Input(i, (baseDir / i).toPath) }
      }
  }
}

class Compiler(
  binary: File,
  configFile: File,
  baseDir: File,
  targetDir: File,
  logger: ManagedLogger,
  nodeModules: File,
  shell: Shell = new Shell,
  prepareWebpackConfig: PrepareWebpackConfig = new PrepareWebpackConfig,
  computeEntryPoints: ComputeEntryPoints = new ComputeEntryPoints,
  computeDependencyTree: ComputeDependencyTree = new ComputeDependencyTree
) {

  def compile(entryPoints: Map[String, Seq[String]], inputFiles: Seq[Path]): CompilationResult = {
    import sbt._

    val inputs = inputFiles.map { i => Input(baseDir.relativize(i.toFile).get.toString, i) }

    val filteredEntries = computeEntryPoints(baseDir, targetDir, entryPoints, inputs)

    if (filteredEntries.isEmpty) {
      return CompilationResult(true, Seq.empty)
    }

    val cmd = Seq(
      binary.getCanonicalPath,
      "--config", prepareWebpackConfig(configFile, filteredEntries, targetDir),
    ).mkString(" ")

    logger.info(cmd)
    val exitCode = shell.execute(cmd, baseDir, "NODE_PATH" -> nodeModules.getCanonicalPath)
    logger.info(s"[Webpack] exited with $exitCode")
    val success = exitCode == 0

    CompilationResult(
      success = success,
      entries = if (success) {
        val dependencyMap = computeDependencyTree(targetDir / "dependency-tree.json")
        val validInputs = inputs.map(_.name).toSet

        filteredEntries
          .toSeq
          .flatMap { case (output, is) =>
            is.map { input => input -> output }
          }
          .groupBy { case (input, _) => input }
          .map { case (_, items) =>
              items.head._1 -> items.map(_._2)
          }
          .toSeq
          .filter { case (input, _) => validInputs.contains(input.name) }
          .sortBy(_._1.name)
          .map { case (input, outputs) =>
            val dependencies = dependencyMap
              .getOrElse(input.name, Set.empty)
              .map { relativePath =>
                (baseDir / relativePath).toPath
              }
            CompilationEntry(input.path.toFile, dependencies, outputs.map(_.toPath).toSet)
          }
      } else {
        Seq.empty
      }
    )
  }
}
