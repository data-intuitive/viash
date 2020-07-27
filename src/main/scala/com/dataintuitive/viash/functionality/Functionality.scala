package com.dataintuitive.viash.functionality

import scala.io.Source
import io.circe.yaml.parser
import java.nio.file.Paths
import java.io.File
import dataobjects._
import resources._
import com.dataintuitive.viash.helpers.IOHelper
import java.net.URI

case class Functionality(
  name: String,
  version: Option[String],
  resources: List[Resource],
  description: Option[String] = None,
  function_type: Option[FunctionType] = None,
  arguments: List[DataObject[_]] = Nil,
  tests: Option[List[Resource]] = None,
  set_wd_to_resources_dir: Option[Boolean] = None,
  private var _rootDir: Option[File] = None // :/
) {

  require(resources.length > 0, message = "resources should contain at least one resource")

  // check whether there are not multiple positional arguments with multiplicity >1
  // and if there is one, whether its position is last
  {
    val positionals = arguments.filter(_.otype == "")
    val multiix = positionals.indexWhere(_.multiple)

    require(
      multiix == -1 || multiix == positionals.length - 1,
      message = s"positional argument ${positionals(multiix).name} should be last since it has multiplicity >1"
    )
  }

  def mainScript: Option[Script] =
    resources.head match {
      case s: Script => Some(s)
      case _ => None
    }

  def mainCode = mainScript.flatMap(_.read)
}

object Functionality {
  def parse(uri: URI): Functionality = {
    val str = IOHelper.read(uri)
    val fun = parser.parse(str)
      .fold(throw _, _.as[Functionality])
      .fold(throw _, identity)

    val resources = fun.resources.map(makeResourcePathAbsolute(_, uri))
    val tests = fun.tests.getOrElse(Nil).map(makeResourcePathAbsolute(_, uri))

    fun.copy(
      resources = resources,
      tests = Some(tests)
    )
  }

  private def makeResourcePathAbsolute(res: Resource, parent: URI) = {
    if (res.isInstanceOf[Executable] || res.path.isEmpty || res.path.get.contains("://")) {
        res
      } else {
        val p = Paths.get(res.path.get).toFile()
        if (p.isAbsolute) {
          res
        } else {
          val newPath = Some(parent.resolve(res.path.get).toString())
          res match {
            case s: BashScript => s.copy(path = newPath)
            case s: PythonScript => s.copy(path = newPath)
            case s: RScript => s.copy(path = newPath)
            case f: PlainFile => f.copy(path = newPath)
          }
        }
      }
  }
}

sealed trait FunctionType
case object AsIs extends FunctionType
case object Convert extends FunctionType
case object ToDir extends FunctionType
case object Join extends FunctionType
