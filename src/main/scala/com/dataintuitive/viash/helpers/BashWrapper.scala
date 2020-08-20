package com.dataintuitive.viash.helpers

import com.dataintuitive.viash.functionality._
import com.dataintuitive.viash.functionality.resources._
import java.nio.file.Paths
import scala.io.Source
import com.dataintuitive.viash.functionality.dataobjects._

object BashWrapper {
  def escape(str: String) = {
    str.replaceAll("([\\\\$`])", "\\\\$1")
  }

  def store(env: String, value: String, multiple_sep: Option[Char]) = {
    if (multiple_sep.isDefined) {
      s"""if [ -z "$$$env" ]; then
         |  $env=$value
         |else
         |  $env="$$$env${multiple_sep.get}"$value
         |fi""".stripMargin.split("\n")
    } else {
      Array(env + "=" + value)
    }
  }

  def argStore(
    name: String,
    plainName: String,
    store: String,
    argsConsumed: Int,
    multiple_sep: Option[Char] = None
  ) = {
    s"""        $name)
       |            ${this.store(plainName, store, multiple_sep).mkString("\n            ")}
       |            shift $argsConsumed
       |            ;;""".stripMargin
  }
  def argStoreSed(name: String, plainName: String, multiple_sep: Option[Char] = None) = {
    argStore(name + "=*", plainName, "$(ViashRemoveFlags \"$1\")", 1, multiple_sep)
  }

  def spaceCode(str: String) = {
    if (str != "") {
      "\n" + str + "\n"
    } else {
      str
    }
  }

  def wrapScript(
      executor: String,
      functionality: Functionality,
      resourcesPath: String = "\\$VIASH_RESOURCES_DIR",
      setupCommands: String,
      dockerfileCommands: String,
      preParse: String,
      parsers: String,
      postParse: String,
      postRun: String
    ) = {
    val mainResource = functionality.mainScript

    // check whether the wd needs to be set to the resources dir
    val cdToResources =
      if (functionality.set_wd_to_resources_dir.getOrElse(false)) {
        "\ncd \"" + resourcesPath + "\""
      } else {
        ""
      }

    // DETERMINE HOW TO RUN THE CODE
    val executionCode = mainResource match {
      case None => ""
      case Some(e: Executable) => {
        e.path.get + " $VIASH_EXECUTABLE_ARGS"
      }
      case Some(res) => {
        val code = res.readWithPlaceholder(functionality).get
        val escapedCode = escape(code)
          .replaceAll("\\\\\\$VIASH_RESOURCES_DIR", resourcesPath)
          .replaceAll("\\\\\\$VIASH_DOLLAR\\\\\\$", "\\$")
          .replaceAll("\\\\\\$VIASH_", "\\$VIASH_")
          .replaceAll("\\\\\\$\\{VIASH_", "\\${VIASH_")
        s"""
          |set -e
          |tempscript=\\$$(mktemp /tmp/viash-run-${functionality.name}-XXXXXX)
          |function clean_up {
          |  rm "\\$$tempscript"
          |}
          |trap clean_up EXIT
          |cat > "\\$$tempscript" << 'VIASHMAIN'
          |${escapedCode}
          |VIASHMAIN$cdToResources
          |${res.command("\\$tempscript")}
          |""".stripMargin
      }
    }

    // generate bash document
    val (heredocStart, heredocEnd) = mainResource match {
      case None => ("", "")
      case Some(e: Executable) => ("", "")
      case _ => ("cat << VIASHEOF | ", "\nVIASHEOF")
    }

    val params = functionality.arguments.filter(d => d.direction == Input || d.isInstanceOf[FileObject])
    val (parPreParse, parParsers, parPostParse) = generateParsers(functionality, params)

    val execPostParse =
      mainResource match {
        case Some(e: Executable) => generateExecutableArgs(params)
        case _ => ""
      }

    /* GENERATE BASH SCRIPT */
    s"""#!/usr/bin/env bash
      |
      |set -e
      |
      |# define helper functions
      |${BashHelper.ViashQuote}
      |${BashHelper.ViashRemoveFlags}
      |${BashHelper.ViashSourceDir}
      |
      |# find source folder of this component
      |VIASH_RESOURCES_DIR=`ViashSourceDir $${BASH_SOURCE[0]}`
      |
      |# helper function for installing extra requirements for this component
      |function ViashDockerfile {
      |$dockerfileCommands
      |}
      |
      |# helper function for installing extra requirements for this component
      |
      |function ViashSetup {
      |$setupCommands
      |}
      |${generateHelp(functionality, params)}
      |${spaceCode(parPreParse)}
      |${spaceCode(preParse)}
      |# initialise array
      |VIASH_POSITIONAL_ARGS=''
      |
      |while [[ $$# -gt 0 ]]; do
      |    case "$$1" in
      |        -h|--help)
      |            ViashHelp
      |            exit;;
      |        ---setup)
      |            ViashSetup
      |            exit 0
      |            ;;
      |        ---dockerfile)
      |            ViashDockerfile
      |            exit 0
      |            ;;
      |$parParsers
      |$parsers
      |        *)    # positional arg or unknown option
      |            # since the positional args will be eval'd, can we always quote, instead of using ViashQuote?
      |            VIASH_POSITIONAL_ARGS="$$VIASH_POSITIONAL_ARGS '$$1'"
      |            shift # past argument
      |            ;;
      |    esac
      |done
      |
      |# parse positional parameters
      |eval set -- $$VIASH_POSITIONAL_ARGS
      |${spaceCode(parPostParse)}
      |${spaceCode(postParse)}
      |${spaceCode(execPostParse)}
      |$heredocStart$executor $executionCode$heredocEnd
      |${spaceCode(postRun)}""".stripMargin
  }


  def generateHelp(functionality: Functionality, params: List[DataObject[_]]): String = {
    // gather parse code for params
    val usageStrs = params.map(param => {
      val names = param.alternatives ::: List(param.name)

      val exval = param.`type`
      val exampleValues =
        if (param.multiple) {
          exval + "1" + param.multiple_sep + exval + "2" + param.multiple_sep + "..."
        } else {
          exval
        }

      val exampleStrs =
        if (param.isInstanceOf[BooleanObject] && param.asInstanceOf[BooleanObject].flagValue.isDefined) {
          names
        } else {
          names.map(name => {
            if (name.startsWith("--") || name.startsWith("---")) {
              name + "=" + exampleValues
            } else if (name.startsWith("-")) {
              name + " " + exampleValues
            } else {
              exampleValues
            }
          })
        }
      val exampleStr = exampleStrs.mkString(", ")

      val properties =
        List("type: " + param.`type`) :::
        { if (param.required) List("required parameter") else Nil } :::
        { if (param.multiple) List("multiple values allowed") else Nil } :::
        { if (param.default.isDefined) List("default: " + param.default.get) else Nil }

      val part1 = "    " + exampleStrs.mkString(", ")
      val part2 = "        " + properties.mkString(", ")
      val part3 = param.description.toList.flatMap(escape(_).split("\n")).map("        " + _)

      (part1 :: part2 :: part3 ::: List("")).map("    echo \"" + _ + "\"").mkString("\n")
    })

    // TODO: add usage?

    s"""# ViashHelp: Display helpful explanation about this executable
       |function ViashHelp {
       |   echo "${escape(functionality.description.getOrElse("").stripLineEnd)}"
       |   echo
       |   echo "Options:"
       |${usageStrs.mkString("\n")}
       |}""".stripMargin
  }

  def generateParsers(functionality: Functionality, params: List[DataObject[_]]) = {
    // gather parse code for params
    val wrapperParams = params.filterNot(_.otype == "")
    val parseStrs = wrapperParams.map(param => {

      if (param.isInstanceOf[BooleanObject] && param.asInstanceOf[BooleanObject].flagValue.isDefined) {
        val bo = param.asInstanceOf[BooleanObject]
        val fv = bo.flagValue.get

        // params of the form --param
        val part1 = argStore(param.name, param.VIASH_PAR, fv.toString(), 1)
        // Alternatives
        val moreParts = param.alternatives.map(alt => {
          argStore(alt, param.VIASH_PAR, fv.toString(), 1)
        })

        (part1 :: moreParts).mkString("\n")
      } else {
        val multisep = if (param.multiple) Some(param.multiple_sep) else None

        // params of the form --param ...
        val part1 = param.otype match {
          case "---" | "--" | "-" => argStore(param.name, param.VIASH_PAR, "\"$2\"", 2, multisep)
          case "" => Nil
        }
        // params of the form --param=..., except -param=... is not allowed
        val part2 = param.otype match {
            case "---" | "--" => List(argStoreSed(param.name, param.VIASH_PAR, multisep))
            case "-" | "" => Nil
          }
        // Alternatives
        val moreParts = param.alternatives.map(alt => {
          argStore(alt, param.VIASH_PAR, "\"$2\"", 2, multisep)
        })

        (part1 :: part2 ::: moreParts).mkString("\n")
      }
    }).mkString("\n")

    // parse positionals
    val positionals = params.filter(_.otype == "")
    val positionalStr = positionals.map{ param =>
      if (param.multiple) {
        s"""while [[ $$# -gt 0 ]]; do
           |  ${store(param.VIASH_PAR, "\"$1\"", Some(param.multiple_sep)).mkString("\n  ")}
           |  shift 1
           |done""".stripMargin
      } else {
        s"""${param.VIASH_PAR}="$$1"; shift 1"""
      }
    }.mkString("\n")

    // construct required checks
    val reqParams = params.filter(p => p.required)
    val reqCheckStr =
      if (reqParams.isEmpty) {
        ""
      } else {
        "\n# check whether required parameters exist\n" +
          reqParams.map{ param =>
            s"""if [ -z "$$${param.VIASH_PAR}" ]; then
               |  echo '${param.name}' is a required argument. Use "--help" to get more information on the parameters.
               |  exit 1
               |fi""".stripMargin
          }.mkString("\n")
      }

    // construct default values, e.g.
    // if [ -z "$VIASH_PAR_FOO" ]; then
    //   VIASH_PAR_FOO="defaultvalue"
    // fi
    val defaultsStrs = params.flatMap{param =>
      // if boolean object has a flagvalue, add the inverse of it as a default value
      val default =
        if (param.required) {
          None
        } else if (param.isInstanceOf[BooleanObject] && param.asInstanceOf[BooleanObject].flagValue.isDefined) {
          param.asInstanceOf[BooleanObject].flagValue.map(!_)
        } else {
          param.default
        }

      default.map(default => {
        s"""if [ -z "$$${param.VIASH_PAR}" ]; then
           |  ${param.VIASH_PAR}="${BashWrapper.escape(default.toString)}"
           |fi""".stripMargin
      })
    }.mkString("\n")

    // construct required file checks
    val reqFiles = params
      .filter(_.isInstanceOf[FileObject])
      .map(_.asInstanceOf[FileObject])
      .filter(_.must_exist)
    val reqFilesStr =
      if (reqFiles.isEmpty) {
        ""
      } else {
        "\n# check whether required files exist\n" +
          reqFiles.map{ param =>
            if (param.multiple) {
              s"""if [ ! -z "$$${param.VIASH_PAR}" ]; then
                 |  IFS=${param.multiple_sep}
                 |  set -f
                 |  for file in $$${param.VIASH_PAR}; do
                 |    if [ ! -e "$$file" ]; then
                 |      echo "File '$$file' does not exist."
                 |      exit 1
                 |    fi
                 |  done
                 |  set +f
                 |  unset IFS
                 |fi""".stripMargin
            } else {
              s"""if [ ! -z "$$${param.VIASH_PAR}" ] && [ ! -e "$$${param.VIASH_PAR}" ]; then
                 |  echo "File '$$${param.VIASH_PAR}' does not exist."
                 |  exit 1
                 |fi""".stripMargin
            }
          }.mkString("\n")
      }

    // return output
    val preParse = ""
    val parsers = parseStrs
    val postParse = positionalStr + "\n" + reqCheckStr + "\n" + defaultsStrs + "\n" + reqFilesStr

    (preParse, parsers, postParse)
  }

  def generateExecutableArgs(params: List[DataObject[_]]) = {
    val inserts = params.map { param =>
      param match {

        case p: BooleanObject if p.flagValue.isDefined => {
          s"""[ "$$${p.VIASH_PAR}" == "${p.flagValue.get}" ] && VIASH_EXECUTABLE_ARGS="$$VIASH_EXECUTABLE_ARGS ${p.name}""""
        }
        case _ => {
          val flag = if (param.otype == "") "" else " " + param.name

          if (param.multiple) {
            s"""if [ ! -z "$$${param.VIASH_PAR}" ]; then
               |  IFS=${param.multiple_sep}
               |  set -f
               |  for val in $$${param.VIASH_PAR}; do
               |    VIASH_EXECUTABLE_ARGS="$$VIASH_EXECUTABLE_ARGS$flag '$$val'"
               |  done
               |  set +f
               |  unset IFS
               |fi""".stripMargin
          } else {
            s"""if [ ! -z "$$${param.VIASH_PAR}" ]; then
               |  VIASH_EXECUTABLE_ARGS="$$VIASH_EXECUTABLE_ARGS$flag '$$${param.VIASH_PAR}'"
               |fi""".stripMargin
          }
        }
      }
    }

    "VIASH_EXECUTABLE_ARGS=''\n" + inserts.mkString("\n")
  }

  private def removeNewlines(s: String) = {
      s.filter(_ >= ' ') // remove all control characters
  }

}
