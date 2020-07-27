package com.dataintuitive.viash

import functionality._
import platforms._
import resources.{BashScript, Script}
import sys.process.{Process, ProcessLogger}
import java.io.{ByteArrayOutputStream, PrintWriter, FileWriter, File}
import java.nio.file.{Paths, Files}

object ViashTester {
  case class TestOutput(name: String, exitValue: Int, output: String)

  def reportTests(results: List[TestOutput], dir: File, verbose: Boolean): Int = {
    if (results.length == 0) {
      println("No tests found!")
      0
    } else {
      println()

      for (res ← results if res.exitValue > 0 && !verbose) {
        println(s">> ${res.name} finished with code ${res.exitValue}:")
        println(res.output)
        println()
      }

      val count = results.count(_.exitValue == 0)

      if (count < results.length) {
        println(s"FAIL! Only $count out of ${results.length} test scripts succeeded!")
        1
      } else {
        println(s"SUCCESS! All $count out of ${results.length} test scripts succeeded!")
        0
      }
    }
  }

  def runTests(fun: Functionality, platform: Platform, dir: File, verbose: Boolean = false) = {
    // build regular executable
    val buildfun = platform.modifyFunctionality(fun)
    val builddir = Paths.get(dir.toString(), "build_executable").toFile()
    builddir.mkdir()
    Main.writeResources(buildfun.resources, builddir)

    // run command, collect output
    val stream = new ByteArrayOutputStream
    val printwriter = new PrintWriter(stream)
    val logwriter = new FileWriter(Paths.get(builddir.toString(), "_viash_build_log.txt").toString(), true)

    val logger: String => Unit =
      (s: String) => {
        if (verbose) println(s)
        printwriter.println(s)
        logwriter.append(s + sys.props("line.separator"))
      }

    // run command, collect output
    val buildResult =
      try {
        val executable = Paths.get(builddir.toString(), fun.name).toString()
        logger(s"+$executable ---setup")
        val exitValue = Process(Seq(executable, "---setup"), cwd = builddir).!(ProcessLogger(logger, logger))

        TestOutput("build_executable", exitValue, stream.toString)
      } finally {
        printwriter.close()
        logwriter.close()
      }

    // generate executable for native platform
    val exe = NativePlatform(version = None).modifyFunctionality(fun).resources.head

    // fetch tests
    val tests = fun.tests.getOrElse(Nil)

    val testResults = tests.filter(_.isInstanceOf[Script]).map { file =>
      val test = file.asInstanceOf[Script]

      // generate bash script for test
      val funonlytest = platform.modifyFunctionality(fun.copy(
        arguments = Nil,
        resources = List(test),
        set_wd_to_resources_dir = Some(true)
      ))
      val testbash = BashScript(
        name = Some(test.filename),
        text = funonlytest.resources.head.text
      )

      // assemble full resources list for test
      val funfinal = fun.copy(resources =
        testbash ::                               // the test, wrapped in a bash script
        exe ::                                    // the executable, wrapped with a native platform,
                                                  // to be run inside of the platform of the test
        funonlytest.resources.tail :::            // other resources generated by wrapping the test script
        fun.resources.tail :::                    // other resources provided in fun.resources
        tests.filter(!_.isInstanceOf[Script])     // other resources provided in fun.tests
      )

      // write resources to dir
      val newdir = Paths.get(dir.toString(), "test_" + test.filename).toFile()
      newdir.mkdir()
      Main.writeResources(funfinal.resources, newdir)

      // run command, collect output
      val stream = new ByteArrayOutputStream
      val printwriter = new PrintWriter(stream)
      val logwriter = new FileWriter(Paths.get(newdir.toString(), "_viash_test_log.txt").toString(), true)

      val logger: String => Unit =
        (s: String) => {
          if (verbose) println(s)
          printwriter.println(s)
          logwriter.append(s + sys.props("line.separator"))
        }

      // run command, collect output
      try {
        val executable = Paths.get(newdir.toString(), testbash.filename).toString()
        val exitValue = Process(Seq(executable), cwd = newdir).!(ProcessLogger(logger, logger))

        TestOutput(test.filename, exitValue, stream.toString)
      } finally {
        printwriter.close()
        logwriter.close()
      }
    }

    buildResult :: testResults
  }
}
