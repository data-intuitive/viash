package com.dataintuitive.viash.targets

import com.dataintuitive.viash.functionality.{Functionality}
import com.dataintuitive.viash.functionality.resources.{Resource, Script}
import scala.io.Source
import java.io.File
import java.nio.file.Paths
import io.circe.yaml.parser
import com.dataintuitive.viash.targets.environments._

trait Target {
  val `type`: String
  def modifyFunctionality(functionality: Functionality, test: Option[Script]): Functionality
}

object Target {
  def parse(file: File): Target = {
    val str = Source.fromFile(file).mkString
    parser.parse(str)
      .fold(throw _, _.as[Target])
      .fold(throw _, identity)
  }
}