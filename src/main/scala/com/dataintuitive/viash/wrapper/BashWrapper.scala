/*
 * Copyright (C) 2020  Data Intuitive
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.dataintuitive.viash.wrapper

import com.dataintuitive.viash.Main
import com.dataintuitive.viash.functionality._
import com.dataintuitive.viash.functionality.resources._
import com.dataintuitive.viash.functionality.dataobjects._
import com.dataintuitive.viash.helpers.{Bash, Format}

object BashWrapper {

  def nameAndVersion(functionality: Functionality): String = {
    functionality.name + functionality.version.map(" " + _).getOrElse("<not versioned>")
  }

  def escape(str: String): String = {
    str.replaceAll("([\\\\$`])", "\\\\$1")
  }

  def escapeViash(str: String, escapeQuotes: Boolean = false): String = {
    val escQuo =
      if (escapeQuotes) {
        (s: String) => s.replaceAll("\"", "\\\"")
      } else {
        (s: String) => s
      }

    escQuo(escape(str))
      .replaceAll("\"", "\\\"")
      .replaceAll("\\\\\\$VIASH_DOLLAR\\\\\\$", "\\$")
      .replaceAll("\\\\\\$VIASH_", "\\$VIASH_")
      .replaceAll("\\\\\\$\\{VIASH_", "\\${VIASH_")
  }

  def store(env: String, value: String, multiple_sep: Option[Char]): Array[String] = {
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
  ): String = {
    s"""        $name)
       |            ${this.store(plainName, store, multiple_sep).mkString("\n            ")}
       |            shift $argsConsumed
       |            ;;""".stripMargin
  }

  def argStoreSed(name: String, plainName: String, multiple_sep: Option[Char] = None): String = {
    argStore(name + "=*", plainName, "$(ViashRemoveFlags \"$1\")", 1, multiple_sep)
  }

  def spaceCode(str: String): String = {
    if (str != "") {
      "\n" + str + "\n"
    } else {
      str
    }
  }

  val var_verbosity = "VIASH_VERBOSITY"
  val var_resources_dir = "VIASH_RESOURCES_DIR"

  def wrapScript(
    executor: String,
    functionality: Functionality,
    mods: BashWrapperMods = BashWrapperMods()
  ): String = {
    val mainResource = functionality.mainScript

    // check whether the wd needs to be set to the resources dir
    val cdToResources =
      if (functionality.set_wd_to_resources_dir.getOrElse(false)) {
        s"""
           |cd "$$$var_resources_dir"
           |PATH="$$$var_resources_dir:\\$$PATH"
           |""".stripMargin
      } else {
        s"""
           |PATH="$$$var_resources_dir:\\$$PATH"
           |""".stripMargin
      }

    // DETERMINE HOW TO RUN THE CODE
    val executionCode = mainResource match {
      case None => ""
      case Some(e: Executable) => " " + e.path.get + " $VIASH_EXECUTABLE_ARGS"
      case Some(res) =>
        val code = res.readWithPlaceholder(functionality).get
        val escapedCode = escapeViash(code)
        s"""
           |set -e
           |tempscript=\\$$(mktemp "$$VIASH_TEMP/viash-run-${functionality.name}-XXXXXX")
           |function clean_up {
           |  rm "\\$$tempscript"
           |}
           |trap clean_up EXIT
           |cat > "\\$$tempscript" << 'VIASHMAIN'
           |$escapedCode
           |VIASHMAIN$cdToResources
           |${res.meta.command("\\$tempscript")}
           |""".stripMargin
    }

    // generate bash document
    val (heredocStart, heredocEnd) = mainResource match {
      case None => ("", "")
      case Some(_: Executable) => ("", "")
      case _ => ("cat << VIASHEOF | ", "\nVIASHEOF")
    }

    // generate script modifiers
    val params = functionality.arguments.filter(d => d.direction == Input || d.isInstanceOf[FileObject])
    val paramAndDummies = functionality.argumentsAndDummies.filter(d => d.direction == Input || d.isInstanceOf[FileObject])

    val helpMods = generateHelp(functionality, params)
    val parMods = generateParsers(params, paramAndDummies)
    val execMods = mainResource match {
      case Some(_: Executable) => generateExecutableArgs(params)
      case _ => BashWrapperMods()
    }

    // combine
    val allMods = helpMods ++ parMods ++ mods ++ execMods

    // generate header
    val nav = nameAndVersion(functionality)
    val nameAndVersionHeader =
      ("#" * (nav.length+10)) + "\n" +
        "#    " + nav + "    #\n" +
        ("#" * (nav.length+10))

    val authorHeader =
      if (functionality.authors.isEmpty) {
        ""
      } else {
        functionality.authors.map(_.toString).mkString("#\n# Component authors:\n# * ", "\n# * ", "\n")
      }

    val header =
      s"""This wrapper script is auto-generated by ${Main.name} ${Main.version} and is thus a derivative work thereof. This software comes with ABSOLUTELY NO WARRANTY from Data Intuitive.
         |
         |The component may contain files which fall under a different license. The authors of this component should specify the license in the header of such files, or include a separate license file detailing the licenses of all included files.""".stripMargin

    /* GENERATE BASH SCRIPT */
    s"""#!/usr/bin/env bash
       |
       |$nameAndVersionHeader
       |
       |# ${Format.wordWrap(header, 78).mkString("\n# ")}
       |$authorHeader
       |set -e
       |
       |if [ -z "$$VIASH_TEMP" ]; then
       |  VIASH_TEMP=/tmp
       |fi
       |
       |# define helper functions
       |${Bash.ViashQuote}
       |${Bash.ViashRemoveFlags}
       |${Bash.ViashSourceDir}
       |${Bash.ViashLogging}
       |
       |# find source folder of this component
       |$var_resources_dir=`ViashSourceDir $${BASH_SOURCE[0]}`
       |
       |${spaceCode(allMods.preParse)}
       |# initialise array
       |VIASH_POSITIONAL_ARGS=''
       |
       |while [[ $$# -gt 0 ]]; do
       |    case "$$1" in
       |        -h|--help)
       |            ViashHelp
       |            exit
       |            ;;
       |        -v|--verbose)
       |            let "$var_verbosity=$var_verbosity+1"
       |            shift 1
       |            ;;
       |        -vv)
       |            let "$var_verbosity=$var_verbosity+2"
       |            shift 1
       |            ;;
       |        --verbosity)
       |            $var_verbosity="$$2"
       |            shift 2
       |            ;;
       |        --verbosity=*)
       |            $var_verbosity="$$(ViashRemoveFlags "$$1")"
       |            shift 1
       |            ;;
       |        --version)
       |            echo "${nameAndVersion(functionality)}"
       |            exit
       |            ;;
       |${allMods.parsers}
       |        *)  # positional arg or unknown option
       |            # since the positional args will be eval'd, can we always quote, instead of using ViashQuote
       |            VIASH_POSITIONAL_ARGS="$$VIASH_POSITIONAL_ARGS '$$1'"
       |            shift # past argument
       |            ;;
       |    esac
       |done
       |
       |# parse positional parameters
       |eval set -- $$VIASH_POSITIONAL_ARGS
       |${spaceCode(allMods.postParse)}
       |$heredocStart$executor$executionCode$heredocEnd
       |${spaceCode(allMods.postRun)}""".stripMargin
  }


  private def generateHelp(functionality: Functionality, params: List[DataObject[_]]) = {
    // gather parse code for params
    val paramStrs = params.map(param => {
      val names = param.alternatives ::: List(param.name)

      val unnamedProps = List(
        ("required parameter", param.required),
        ("multiple values allowed", param.multiple),
        ("output", param.direction == Output),
        ("file must exist", param.isInstanceOf[FileObject] && param.asInstanceOf[FileObject].must_exist)
      ).filter(_._2).map(_._1)

      val namedProps = List(
        ("type", Some((param.oType :: unnamedProps).mkString(", "))),
        ("default", param.default),
        ("example", param.example)
      ).flatMap { case (name, x) =>
        x.map("\necho \"        " + name + ": " + _ + "\"")
      }.mkString

      val descStr = param.description.map{ desc =>
        val escapedDesc = escapeViash(desc, escapeQuotes = true).split("\n")
        escapedDesc.map("\necho \"        " + _ + "\"").mkString
      }.getOrElse("")

      s"""
         |echo "    ${names.mkString(", ")}"$namedProps$descStr
         |echo ""
         |""".stripMargin
    })

    val descrStr =
      if (functionality.description.isDefined) {
        s"""
           |echo "${escapeViash(functionality.description.get.stripLineEnd, escapeQuotes = true)}"""".stripMargin
      } else {
        ""
      }

    val usageStr =
      if (functionality.usage.isDefined) {
        s"""
           |echo "Usage: ${escapeViash(functionality.usage.get.stripLineEnd, escapeQuotes = true)}"
           |echo""".stripMargin
      } else {
        ""
      }

    val preParse =
      s"""# ViashHelp: Display helpful explanation about this executable
      |function ViashHelp {
      |   echo "${nameAndVersion(functionality)}"$descrStr
      |   echo$usageStr
      |   echo "Options:"
      |${paramStrs.mkString("\n")}
      |}""".stripMargin

    BashWrapperMods(preParse = preParse)
  }

  private def generateParsers(params: List[DataObject[_]], paramsAndDummies: List[DataObject[_]]) = {
    // gather parse code for params
    val wrapperParams = params.filterNot(_.otype == "")
    val parseStrs = wrapperParams.map {
      case bo: BooleanObject if bo.flagValue.isDefined =>
        val fv = bo.flagValue.get

        // params of the form --param
        val part1 = argStore(bo.name, bo.VIASH_PAR, fv.toString, 1)
        // Alternatives
        val moreParts = bo.alternatives.map(alt => {
          argStore(alt, bo.VIASH_PAR, fv.toString, 1)
        })

        (part1 :: moreParts).mkString("\n")
      case param =>
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
    }.mkString("\n")

    // parse positionals
    val positionals = paramsAndDummies.filter(_.otype == "")
    val positionalStr = positionals.map { param =>
      if (param.multiple) {
        s"""while [[ $$# -gt 0 ]]; do
           |  ${store(param.VIASH_PAR, "\"$1\"", Some(param.multiple_sep)).mkString("\n  ")}
           |  shift 1
           |done""".stripMargin
      } else {
        s"""if [[ $$# -gt 0 ]]; then
           |  ${param.VIASH_PAR}="$$1"
           |  shift 1
           |fi"""
      }
    }.mkString("\n")

    // construct required checks
    val reqParams = paramsAndDummies.filter(_.required)
    val reqCheckStr =
      if (reqParams.isEmpty) {
        ""
      } else {
        "\n# check whether required parameters exist\n" +
          reqParams.map { param =>
            s"""if [ -z "$$${param.VIASH_PAR}" ]; then
               |  ViashError '${param.name}' is a required argument. Use "--help" to get more information on the parameters.
               |  exit 1
               |fi""".stripMargin
          }.mkString("\n")
      }

    // construct default values, e.g.
    // if [ -z "$VIASH_PAR_FOO" ]; then
    //   VIASH_PAR_FOO="defaultvalue"
    // fi
    val defaultsStrs = paramsAndDummies.flatMap { param =>
      // if boolean object has a flagvalue, add the inverse of it as a default value
      val default = param match {
        case p if p.required => None
        case bo: BooleanObject if bo.flagValue.isDefined => bo.flagValue.map(!_)
        case p => p.default
      }

      default.map(default => {
        s"""if [ -z "$$${param.VIASH_PAR}" ]; then
           |  ${param.VIASH_PAR}="${escapeViash(default.toString, escapeQuotes = true)}"
           |fi""".stripMargin
      })
    }.mkString("\n")

    // construct required file checks
    val reqFiles = paramsAndDummies.flatMap {
      case f: FileObject if f.must_exist => Some(f)
      case _ => None
    }
    val reqFilesStr =
      if (reqFiles.isEmpty) {
        ""
      } else {
        "\n# check whether required files exist\n" +
          reqFiles.map { param =>
            if (param.multiple) {
              s"""if [ ! -z "$$${param.VIASH_PAR}" ]; then
                 |  IFS=${param.multiple_sep}
                 |  set -f
                 |  for file in $$${param.VIASH_PAR}; do
                 |    unset IFS
                 |    if [ ! -e "$$file" ]; then
                 |      ViashError "File '$$file' does not exist."
                 |      exit 1
                 |    fi
                 |  done
                 |  set +f
                 |fi""".stripMargin
          } else {
            s"""if [ ! -z "$$${param.VIASH_PAR}" ] && [ ! -e "$$${param.VIASH_PAR}" ]; then
               |  ViashError "File '$$${param.VIASH_PAR}' does not exist."
               |  exit 1
               |fi""".stripMargin
            }
          }.mkString("\n")
      }

    // return output
    BashWrapperMods(
      parsers = parseStrs,
      postParse = positionalStr + "\n" + reqCheckStr + "\n" + defaultsStrs + "\n" + reqFilesStr
    )
  }

  private def generateExecutableArgs(params: List[DataObject[_]]) = {
    val inserts = params.map {
      case bo: BooleanObject if bo.flagValue.isDefined =>
        s"""
           |if [ "$$${bo.VIASH_PAR}" == "${bo.flagValue.get}" ]; then
           |  VIASH_EXECUTABLE_ARGS="$$VIASH_EXECUTABLE_ARGS ${bo.name}"
           |fi""".stripMargin
      case param =>
        val flag = if (param.otype == "") "" else " " + param.name

        if (param.multiple) {
          s"""
             |if [ ! -z "$$${param.VIASH_PAR}" ]; then
             |  IFS=${param.multiple_sep}
             |  set -f
             |  for val in $$${param.VIASH_PAR}; do
             |    unset IFS
             |    VIASH_EXECUTABLE_ARGS="$$VIASH_EXECUTABLE_ARGS$flag '$$val'"
             |  done
             |  set +f
             |fi""".stripMargin
        } else {
          s"""
             |if [ ! -z "$$${param.VIASH_PAR}" ]; then
             |  VIASH_EXECUTABLE_ARGS="$$VIASH_EXECUTABLE_ARGS$flag '$$${param.VIASH_PAR}'"
             |fi""".stripMargin
        }
    }

    BashWrapperMods(
      postParse = "\nVIASH_EXECUTABLE_ARGS=''" + inserts.mkString
    )
  }

}
