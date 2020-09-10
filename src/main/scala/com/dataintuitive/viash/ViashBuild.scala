package com.dataintuitive.viash

import config._
import functionality.resources.PlainFile
import io.circe.yaml.Printer
import helpers.IOHelper
import java.nio.file.Paths

object ViashBuild {
  def apply(config: Config, output: String, printMeta: Boolean = false, namespace: Option[String] = None) {
    val fun = config.functionality

    // create dir
    val dir = new java.io.File(output)
    dir.mkdirs()

    // create Config Resource
    // Options: https://github.com/circe/circe-yaml/blob/master/src/main/scala/io/circe/yaml/Printer.scala
    val printer = Printer(
      dropNullKeys = true,
      mappingStyle = Printer.FlowStyle.Block,
      splitLines = true
    )
    val strippedConfig = config.copy(
      info = config.info.map(_.copy(
        output_path = Some(output),
        executable_path = fun.mainScript.map(scr => Paths.get(output, scr.name.get).toString)
      )),
      platforms = Nil // drop other platforms
    ).copy(
      functionality = config.functionality.copy(namespace = namespace)
    )

    val configYaml = PlainFile(
      name = Some("viash.yaml"),
      text = Some(printer.pretty(encodeConfig(strippedConfig)))
    )

    IOHelper.writeResources(configYaml :: fun.resources.getOrElse(Nil), dir)

    if (printMeta) {
      println(config.info.get.consoleString)
    }
  }
}
