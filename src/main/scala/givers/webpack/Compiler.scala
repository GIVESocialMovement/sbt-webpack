package givers.webpack

import java.io.{File, FileOutputStream, PrintWriter}
import java.nio.file.{Files, Path}

import play.api.libs.json._
import sbt.internal.util.ManagedLogger

import scala.io.Source

case class CompilationResult(success: Boolean, entries: Seq[CompilationEntry])
case class CompilationEntry(inputFile: File, filesWritten: Set[Path])
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

  private[this] def readAndClose(file: File): String = {
    val s = scala.io.Source.fromFile(file)

    try {
      s.mkString
    } finally {
      s.close()
    }
  }

  def apply(file: File): Map[String, Set[String]] = {
    apply(readAndClose(file))
  }

  // The result is: input file -> Set[output file]
  def apply(content: String): Map[String, Set[String]] = {
    val json = Json.parse(content)

    json.as[JsArray].value
      .flatMap { obj =>
        val output = obj("output").as[String]

        obj("dependencies").as[Seq[JsValue]].flatMap {
          case JsString(s) => Some(s -> output)
          case _ => None
        }
      }
      .groupBy(_._1)
      .mapValues(_.map(_._2).toSet)
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
          |module.exports.output.path = '${targetDir.getCanonicalPath.replace("\\","\\\\")}';
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
    logger.info(s"[sbt-webpack] exited with $exitCode")
    val success = exitCode == 0

    CompilationResult(
      success = success,
      entries = if (success) {
        val dependencyMap = computeDependencyTree(targetDir / "dependency-tree.json")
        logger.debug(s"[sbt-webpack] dependency map: $dependencyMap")

        dependencyMap
          .map { case (input, outputs) =>
            CompilationEntry(new File(input), outputs.map { output => targetDir.toPath.resolve(output) })
          }
          .toSeq
          .sortBy(_.inputFile)
      } else {
        Seq.empty
      }
    )
  }
}
