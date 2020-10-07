package com.dataintuitive.viash.functionality.resources

import com.dataintuitive.viash.functionality.Functionality

trait Script extends Resource {
  val meta: ScriptObject

  def generatePlaceholder(functionality: Functionality): String

  def readWithPlaceholder(implicit functionality: Functionality): Option[String] = {
    read.map(code => {
      val lines = code.split("\n")
      val startIndex = lines.indexWhere(_.contains("VIASH START"))
      val endIndex = lines.indexWhere(_.contains("VIASH END"))

      val li =
        Array(
          meta.commentStr + " The following code has been auto-generated by Viash.",
          generatePlaceholder(functionality)
        ) ++ {
          if (startIndex >= 0 && endIndex >= 0) {
            lines.slice(0, startIndex + 1) ++ lines.slice(endIndex, lines.length)
          } else {
            lines
          }
        }

      li.mkString("\n")
    })
  }
}

trait ScriptObject {
  val commentStr: String
  val extension: String
  def command(script: String): String
  def commandSeq(script: String): Seq[String]
  def apply(
    name: Option[String] = None,
    path: Option[String] = None,
    text: Option[String] = None,
    is_executable: Boolean = true
  ): Script
}

object Script {
  val extMap =
    List(BashScript, PythonScript, RScript, JavaScriptScript, ScalaScript)
      .map(x => (x.extension.toLowerCase, x))
      .toMap

  def fromExt(extension: String): ScriptObject = {
    new RuntimeException("Unrecognised script extension: " + extension)
    extMap(extension.toLowerCase)
  }
}
