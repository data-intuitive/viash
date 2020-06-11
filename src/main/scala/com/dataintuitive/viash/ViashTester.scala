package com.dataintuitive.viash

import functionality._
import targets._
import resources.Script

import java.nio.file.{Paths, Files}

object ViashTester {
  def testFunctionality(fun: Functionality, platform: Target, verbose: Boolean = false) = {
    // generate executable for native target
    val exe = NativeTarget().modifyFunctionality(fun).resources.head

    // fetch tests
    val tests = fun.tests.getOrElse(Nil)
    val executableTests = tests.filter(_.isInstanceOf[Script]).map(_.asInstanceOf[Script])

    executableTests.map{ test =>
      val funonlytest = platform.modifyFunctionality(fun.copy(
        resources = List(test),
        set_wd_to_resources_dir = Some(true)
      ))

      import com.dataintuitive.viash.functionality.resources.BashScript
      val pimpedTest = BashScript(
        name = Some(test.filename),
        text = funonlytest.resources.head.text
      )

      val funfinal = funonlytest.copy(resources =
        pimpedTest ::                             // the test, wrapped in a bash script
        exe ::                                    // the executable, wrapped with a native platform,
                                                  // to be run inside of the platform of the test
        funonlytest.resources.tail :::            // other resources generated by wrapping the test script
        fun.resources.tail :::                    // other resources provided in fun.resources
        tests.filter(_.filename != test.filename) // other resources provided in fun.tests
      )

      val dir = Files.createTempDirectory("viash_" + funfinal.name).toFile()
      Main.writeResources(funfinal.resources, funfinal.rootDir, dir)

      // execute with parameters
      val executable = Paths.get(dir.toString(), test.filename).toString()

      // run command, collect output
      import sys.process._
      import java.io._
      val stream = new ByteArrayOutputStream
      val writer = new PrintWriter(stream)

      val logger: String => Unit = if (verbose) {
        (s: String) => {
          println(s)
          writer.println(s)
        }
      } else {
        writer.println
      }

      // run setup
      // todo: should only do this once...
      logger(s"+ $executable ---setup")
      Process(Seq(executable, "---setup"), cwd = dir).!(ProcessLogger(logger, logger))

      // run command, collect output
      val exitValue = Process(Seq(executable), cwd = dir).!(ProcessLogger(logger, logger))
      writer.close()

      (test.filename, exitValue, stream.toString)
    }
  }
}