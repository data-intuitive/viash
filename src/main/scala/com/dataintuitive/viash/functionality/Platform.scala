package com.dataintuitive.viash.functionality

sealed trait Platform {
  def command(script: String): String
  
  def generateArgparse(functionality: Functionality): String
}

case object R extends Platform {
  def command(script: String) = {
    "Rscript " + script
  }
  
  def generateArgparse(functionality: Functionality): String = {
    val params = functionality.inputs ::: functionality.outputs.filter(_.isInstanceOf[FileObject])
    
    // gather params for optlist
    val paramOptions = params.map {
      case o: BooleanObject => {
        val helpStr = o.description.map(", help = \"" + _ + "\"").getOrElse("")
        val defaultStr = o.default.map(d => ", default = " + { if (d) "TRUE" else "FALSE" }).getOrElse("")
        s"""make_option("--${o.name}", type = "logical"$helpStr$defaultStr)"""
      }
      case o: DoubleObject => {
        val helpStr = o.description.map(", help = \"" + _ + "\"").getOrElse("")
        val defaultStr = o.default.map(d => ", default = " + d).getOrElse("")
        s"""make_option("--${o.name}", type = "double"$helpStr$defaultStr)"""
      }
      case o: IntegerObject => {
        val helpStr = o.description.map(", help = \"" + _ + "\"").getOrElse("")
        val defaultStr = o.default.map(d => ", default = " + d).getOrElse("")
        s"""make_option("--${o.name}", type = "integer"$helpStr$defaultStr)"""
      }
      case o: StringObject => {
        val helpStr = o.description.map(", help = \"" + _ + "\"").getOrElse("")
        val defaultStr = o.default.map(d => ", default = \"" + d + "\"").getOrElse("")
        s"""make_option("--${o.name}", type = "character"$helpStr$defaultStr)"""
      }
      case o: FileObject => {
        val helpStr = o.description.map(", help = \"" + _ + "\"").getOrElse("")
        val defaultStr = o.default.map(d => ", default = \"" + d + "\"").getOrElse("")
        s"""make_option("--${o.name}", type = "character"$helpStr$defaultStr)"""
      }
    }
    
    // gather description 
    val descrStr = functionality.description.map("\ndescription = \"" + _ + "\",").getOrElse("")
    
    // gather required arg checks
    val reqParams = params.filter(_.required.getOrElse(false))
    val reqStr = 
      if (reqParams.isEmpty) {
        ""
      } else {
        s"""for (required_arg in c("${reqParams.map(_.name).mkString("\", \"")}")) {
        |  if (is.null(par[[required_arg]])) {
        |    stop('"--', required_arg, '" is a required argument. Use "--help" to get more information on the parameters.')
        |  }
        |}
        """.stripMargin
      }
    
    s"""
      |### The following code has been auto-generated by Portash.
      |library(optparse, warn.conflicts = FALSE)
      |
      |arguments <- commandArgs(trailingOnly = TRUE)
      |
      |optlist <- list(
      |${paramOptions.mkString("  ", ",\n  ", "")}
      |)
      |
      |parser <- OptionParser(
      |  usage = "",$descrStr
      |  option_list = optlist
      |)
      |par <- parse_args(parser, args = arguments)
      |
      |# checking inputs
      |$reqStr
      |if (!par$$format %in% c("csv", "tsv", "rds", "h5")) {
      |  stop('format must be one of "csv", "tsv", "rds", or "h5".')
      |}
      |if (!file.exists(par$$input)) {
      |  stop('input file must exist.')
      |}
      """.stripMargin
  }
}

case object Python extends Platform {
  def command(script: String) = {
    "python " + script
  }
  
  def generateArgparse(functionality: Functionality): String = {
    ""
  }
}

object Platform {
  def fromString(str: String) = {
    str match {
      case "R" => R
      case "Python" => Python
      case s => throw new RuntimeException(s"Unrecognised platform '${s}'.") 
    }
  }
}