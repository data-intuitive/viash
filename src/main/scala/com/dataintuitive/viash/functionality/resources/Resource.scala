package com.dataintuitive.viash.functionality.resources

import java.net.URI

import com.dataintuitive.viash.helpers.IO
import java.nio.file.{Path, Paths}

trait Resource {
  val `type`: String
  val name: Option[String]
  val path: Option[String]
  val text: Option[String]
  val is_executable: Boolean

  require(
    path.isEmpty != text.isEmpty,
    message = s"For each resource, either 'path' or 'text' should be defined, the other undefined."
  )
  require(
    name.isDefined || path.isDefined,
    message = s"For each resources, 'name' needs to be defined if no 'path' is defined."
  )

  val uri: Option[URI] = path.map(IO.uri)

  def filename: String = {
    if (name.isDefined) {
      name.get
    } else {
      val path = Paths.get(uri.get.getPath)
      path.getFileName.toString
    }
  }

  def read: Option[String] = {
    if (text.isDefined) {
      text
    } else {
      Some(IO.read(uri.get))
    }
  }

  def write(path: Path, overwrite: Boolean) {
    val file =
      if (text.isDefined) {
        IO.write(text.get, path, overwrite)
      } else {
        IO.write(uri.get, path, overwrite)
      }

    file.setExecutable(is_executable)
  }
}
