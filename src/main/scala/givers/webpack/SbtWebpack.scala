package givers.webpack

import java.io.PrintWriter
import java.nio.file.Paths

import com.typesafe.sbt.web._
import com.typesafe.sbt.web.incremental._
import sbt.Keys._
import sbt._
import xsbti.{Position, Problem, Severity}

import scala.io.Source

object SbtWebpack extends AutoPlugin {

  override def requires = SbtWeb

  override def trigger = AllRequirements

  object autoImport {
    object WebpackKeys {
      val webpack = TaskKey[Seq[File]]("webpack", "Run webpack")
      val binary = SettingKey[File]("webpackBinary", "The location of webpack binary")
      val configFile = SettingKey[File]("webpackConfigFile", "The location of webpack.config.js")
      val entries = SettingKey[Map[String, Seq[String]]]("webpackEntries", "The entry points as defined here: https://webpack.js.org/concepts/entry-points")
      val nodeModulesPath = TaskKey[File]("webpackNodeModules", "The location of the node_modules.")
    }
  }

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport.WebpackKeys._

  override def projectSettings: Seq[Setting[_]] = inConfig(Assets)(Seq(
    excludeFilter in webpack := HiddenFileFilter,
    includeFilter in webpack := "*.js",
    nodeModulesPath := new File("./node_modules"),
    resourceManaged in webpack := webTarget.value / "webpack" / "main",
    managedResourceDirectories in Assets+= (resourceManaged in webpack in Assets).value,
    resourceGenerators in Assets += webpack in Assets,
    webpack in Assets := task.dependsOn(WebKeys.webModules in Assets).value,
    // Because sbt-webpack might compile JS and output into the same file.
    // Therefore, we need to deduplicate the files by choosing the one in the target directory.
    // Otherwise, the "duplicate mappings" error would occur.
    deduplicators in Assets += {
      val targetDir = (resourceManaged in webpack in Assets).value
      val targetDirAbsolutePath = targetDir.getAbsolutePath

      { files: Seq[File] => files.find(_.getAbsolutePath.startsWith(targetDirAbsolutePath)) }
    },
  ))

  private[this] def readAndClose(file: File): String = {
    val s = Source.fromFile(file)

    try {
      s.mkString
    } finally {
      s.close()
    }
  }

  lazy val task = Def.task {
    val baseDir = (baseDirectory in Assets).value
    val targetDir = (resourceManaged in webpack in Assets).value
    val logger = (streams in Assets).value.log
    val nodeModulesLocation = (nodeModulesPath in webpack).value
    val webpackReporter = (reporter in Assets).value
    val webpackBinaryLocation = (binary in webpack).value
    val webpackConfigFileLocation = (configFile in webpack).value
    val webpackEntryPoints = (entries in webpack).value

    val sources = webpackEntryPoints.values.flatMap { vs => vs.map(baseDir / _) }.toSeq

    val globalHash = new String(
      Hash(Seq(
        readAndClose(webpackConfigFileLocation),
        state.value.currentCommand.map(_.commandLine).getOrElse(""),
        sys.env.toList.sorted.toString
      ).mkString("--"))
    )

    val fileHasherIncludingOptions = OpInputHasher[File] { f =>
      OpInputHash.hashString(Seq(
        "sbt-webpack",
        f.getCanonicalPath,
        baseDir.getAbsolutePath,
        globalHash
      ).mkString("--"))
    }

    val results = incremental.syncIncremental((streams in Assets).value.cacheDirectory / "run", sources) { modifiedSources =>
      val startInstant = System.currentTimeMillis

      if (modifiedSources.nonEmpty) {
        logger.info(s"[sbt-webpack] Compile on ${modifiedSources.size} changed files")
        modifiedSources
          .map(_.toString)
          .sorted
          .foreach { s =>
            logger.info(s"[sbt-webpack] - $s")
          }
      } else {
        logger.info(s"[sbt-webpack] No changes to compile")
      }

      val compiler = new Compiler(
        webpackBinaryLocation,
        webpackConfigFileLocation,
        baseDir,
        targetDir,
        logger,
        nodeModulesLocation)

      // Compile all modified sources at once
      val result = compiler.compile(webpackEntryPoints, modifiedSources.map(_.toPath))

      // Report compilation problems
      CompileProblems.report(
        reporter = webpackReporter,
        problems = if (!result.success) {
          Seq(new Problem {
            override def category() = ""

            override def severity() = Severity.Error

            override def message() = ""

            override def position() = new Position {
              override def line() = java.util.Optional.empty()

              override def lineContent() = ""

              override def offset() = java.util.Optional.empty()

              override def pointer() = java.util.Optional.empty()

              override def pointerSpace() = java.util.Optional.empty()

              override def sourcePath() = java.util.Optional.empty()

              override def sourceFile() = java.util.Optional.empty()
            }
          })
        } else { Seq.empty }
      )

      // Collect OpResults
      val (trackedEntries, _) = result.entries.partition { entry =>
        modifiedSources.exists { f => f.getCanonicalPath == entry.inputFile.getCanonicalPath }
      }
      val opResults: Map[File, OpResult] = trackedEntries
        .filter { entry =>
          // Webpack might generate extra files from extra input files. We can't track those input files.
          modifiedSources.exists { f => f.getCanonicalPath == entry.inputFile.getCanonicalPath }
        }
        .map { entry =>
          entry.inputFile -> OpSuccess(Set(entry.inputFile), entry.filesWritten.map(_.toFile))
        }
        .toMap

      // Collect the created files
      val createdFiles = result.entries.flatMap(_.filesWritten.map(_.toFile)).distinct

      val endInstant = System.currentTimeMillis

      if (createdFiles.nonEmpty) {
        logger.info(s"[sbt-webpack] finished compilation in ${endInstant - startInstant} ms and generated ${createdFiles.size} JS files")
        createdFiles
          .map(_.toString)
          .sorted
          .foreach { s =>
            logger.info(s"[sbt-webpack] - $s")
          }
      }

      (opResults, createdFiles)

    }(fileHasherIncludingOptions)

    // Return the dependencies
    (results._1 ++ results._2.toSet).toSeq
  }
}
