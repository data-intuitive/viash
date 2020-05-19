package com.dataintuitive.viash

import functionality._
import targets._
import resources.Script

import java.nio.file.{Paths, Files}
import scala.io.Source
import org.rogach.scallop.Subcommand

import java.nio.charset.StandardCharsets

import sys.process._
import com.dataintuitive.viash.helpers.Exec
import com.dataintuitive.viash.functionality.resources.Resource

object Main {
  def main(args: Array[String]) {
    val (viashArgs, runArgs) = args.span(_ != "--")

    val conf = new CLIConf(viashArgs)

    conf.subcommand match {
      case Some(conf.run) => {
        // create new functionality with argparsed executable
        val (fun, tar) = viashLogic(conf.run)

        // write executable and resources to temporary directory
        val dir = Files.createTempDirectory("viash_" + fun.name).toFile()
        writeResources(fun.resources, fun.rootDir, dir)

        // execute with parameters
        val executable = Paths.get(dir.toString(), fun.name).toString()
        println(Exec.run(
          Array(executable) ++
          runArgs.dropWhile(_ == "--")
        ))
      }
      case Some(conf.export) => {
        // create new functionality with argparsed executable
        val (fun, tar) = viashLogic(conf.export)

        // write files to given output directory
        val dir = new java.io.File(conf.export.output())
        dir.mkdirs()
        writeResources(fun.resources, fun.rootDir, dir)
      }
      case Some(conf.pimp) => {
        // read functionality
        val functionality = readFunctionality(conf.pimp)

        // fetch argparsed code
        val mainCode = functionality.mainCodeWithArgParse.get

        // write to file or stdout
        if (conf.pimp.output.isDefined) {
          val file = new java.io.File(conf.pimp.output())
          Files.write(file.toPath(), mainCode.getBytes(StandardCharsets.UTF_8))
          file.setExecutable(true)
        } else {
          println(mainCode)
        }
      }
      case Some(conf.test) => {
        // create new functionality with argparsed executable
        val (fun, tar) = viashLogic(conf.test)

        val tests = fun.tests.getOrElse(Nil)
        val executableTests = tests.filter(_.isInstanceOf[Script]).map(_.asInstanceOf[Script])

        if (executableTests.length == 0) {
          println("No tests found!")
        } else {
          // write executable and resources to temporary directory
          val dir = Files.createTempDirectory("viash_" + fun.name).toFile()
          writeResources(fun.resources, fun.rootDir, dir)

          // write test resources to same directory
          writeResources(tests, fun.rootDir, dir)

          // execute with parameters
          val executable = Paths.get(dir.toString(), fun.name).toString()

          for (test ← executableTests) {
            println(Exec.run(
              test.commandSeq(test.filename),
              cwd = Some(dir),
              Seq(
                "PATH" → Exec.appendToEnv("PATH", dir.toString()),
                "VIASH_PLATFORM" → tar.`type`
              )
            ))
          }
        }
      }
      case _ => println("No subcommand was specified. See `viash --help` for more information.")
    }
  }

  def readFunctionality(subcommand: WithFunctionality) = {
    val funcPath = new java.io.File(subcommand.functionality()).getAbsoluteFile()
    val functionality = Functionality.parse(funcPath)
    functionality.rootDir = funcPath
    functionality
  }

  def viashLogic(subcommand: WithFunctionality with WithPlatform) = {
    // get the functionality yaml
    // let the functionality object know the path in which it resided,
    // so it can find back its resources
    val functionality = readFunctionality(subcommand)

    // get the platform
    // if no platform is provided, assume the platform
    // should be native and all dependencies are taken care of
    val platform = subcommand.platform.map{ path =>
      val targPath = new java.io.File(path)
      Target.parse(targPath)
    }.getOrElse(NativeTarget())

    // modify the functionality using the target
    val fun2 = platform.modifyFunctionality(functionality)

    (fun2, platform)
  }

  def writeResources(
    resources: Seq[Resource],
    inputDir: java.io.File,
    outputDir: java.io.File,
    overwrite: Boolean = true
  ) {
    // copy all files
    resources.foreach(
      resource => {
        val dest = Paths.get(outputDir.getAbsolutePath, resource.filename)

        val destFile = dest.toFile()
        if (overwrite && destFile.exists()) {
          destFile.delete()
        }

        if (resource.path.isDefined) {
          val sour = Paths.get(inputDir.getPath(), resource.path.get)
          Files.copy(sour, dest)
        } else {
          val text = resource.text.get
          Files.write(dest, text.getBytes(StandardCharsets.UTF_8))
        }

        destFile.setExecutable(resource.is_executable)
      }
    )
  }
}
