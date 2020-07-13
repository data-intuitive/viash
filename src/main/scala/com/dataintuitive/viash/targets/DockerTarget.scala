package com.dataintuitive.viash.targets

import com.dataintuitive.viash.functionality._
import com.dataintuitive.viash.functionality.dataobjects._
import com.dataintuitive.viash.functionality.resources._
import com.dataintuitive.viash.targets.environments._
import java.nio.file.Paths
import com.dataintuitive.viash.helpers.BashHelper
import com.dataintuitive.viash.functionality.resources.Resource

case class DockerTarget(
  image: String,
  target_image: Option[String] = None,
  resolve_volume: ResolveVolume = Manual,
  volumes: List[Volume] = Nil,
  port: Option[List[String]] = None,
  workdir: Option[String] = None,
  apk: Option[ApkEnvironment] = None,
  apt: Option[AptEnvironment] = None,
  r: Option[REnvironment] = None,
  python: Option[PythonEnvironment] = None
) extends Target {
  val `type` = "docker"

  def modifyFunctionality(functionality: Functionality) = {
    val resourcesPath = "/app"

    // collect variables
    val dockerArgs = generateDockerRunArgs(functionality)

    // create setup
    val (imageName, setupCommands) = processDockerSetup(functionality, resourcesPath)

    // process docker mounts
    val (volPreParse, volParsers, volPostParse, volInputs, volExtraParams) = processDockerVolumes(functionality)

    // add docker debug flag
    val debuggor = s"""docker run $dockerArgs -v `pwd`:/pwd --workdir /pwd -t $imageName"""
    val (debPreParse, debParsers, debPostParse, debInputs) = addDockerDebug(debuggor)

    // make commands
    val executor = s"""eval docker run $dockerArgs$volExtraParams $imageName"""

    // add extra arguments to the functionality file for each of the volumes
    val fun2 = functionality.copy(
      arguments = functionality.arguments ::: volInputs ::: debInputs
    )

    // create new bash script
    val bashScript = BashScript(
        name = Some(functionality.name),
        text = Some(BashHelper.wrapScript(
          executor = executor,
          functionality = fun2,
          resourcesPath = "/resources",
          setupCommands = setupCommands,
          preParse = volPreParse + debPreParse,
          parsers = volParsers + debParsers,
          postParse = volPostParse + debPostParse,
          postRun = ""
        )),
        is_executable = true
      )

    fun2.copy(
      resources = bashScript :: fun2.resources.tail
    )
  }

  def processDockerSetup(functionality: Functionality, resourcesPath: String) = {
    // get dependencies
    val aptInstallCommands = apt.map(_.getInstallCommands()).getOrElse(Nil)
    val apkInstallCommands = apk.map(_.getInstallCommands()).getOrElse(Nil)
    val rInstallCommands = r.map(_.getInstallCommands()).getOrElse(Nil)
    val pythonInstallCommands = python.map(_.getInstallCommands()).getOrElse(Nil)

    val runCommands = List(aptInstallCommands, apkInstallCommands, rInstallCommands, pythonInstallCommands)

    // if no extra dependencies are needed, the provided image can just be used,
    // otherwise need to construct a separate docker container
    if (runCommands.flatten.isEmpty) {
      (image, s"docker image inspect $image >/dev/null 2>&1 || docker pull $image")
    } else {
      val imageName = target_image.getOrElse("viash_autogen/" + functionality.name)

      val dockerFile =
        s"FROM $image\n" +
          runCommands.map(li => if (li.isEmpty) "" else li.mkString("RUN ", " && \\\n  ", "\n")).mkString("\n")

      val setupCommands =
        s"""# create temporary directory to store temporary dockerfile in
          |tmpdir=$$(mktemp -d /tmp/viash_setupdocker-${functionality.name}-XXXXXX)
          |function clean_up {
          |  rm -rf "\\$$tmpdir"
          |}
          |trap clean_up EXIT
          |cat > $$tmpdir/Dockerfile << 'VIASHDOCKER'
          |$dockerFile
          |VIASHDOCKER
          |docker build -t $imageName $$tmpdir""".stripMargin
      (imageName, setupCommands)
    }
  }

  def generateDockerRunArgs(functionality: Functionality) = {
    // process port parameter
    val portStr = port.getOrElse(Nil).map("-p " + _ + " ").mkString("")

    // check whether entrypoint should be set to bash
    val entrypointStr = functionality.mainScript match {
      case Some(e: Executable) => "--entrypoint='' "
      case _ => "--entrypoint=bash "
    }

    portStr + entrypointStr + "-i --rm -v \"$RESOURCES_DIR\":/resources"
  }

  def processDockerVolumes(functionality: Functionality) = {
    resolve_volume match {
      case Automatic => processDockerVolumesAutomatic(functionality)
      case Manual => processDockerVolumesManual(functionality)
    }
  }

  def processDockerVolumesAutomatic(functionality: Functionality) = {
    val storeVariable = "VIASHARGS"
    val extraMountsVar = "VIASH_EXTRA_MOUNTS"

    val args = functionality.arguments

    val preParse =
      if (args.isEmpty) {
        ""
      } else {
        s"""${BashHelper.ViashAbsolutePath}
           |${BashHelper.ViashAutodetectMount}
           |${BashHelper.ViashExtractFlags}
           |$extraMountsVar="" """.stripMargin
      }

   val parsers1 =
      if (args.isEmpty) {
        ""
      } else {
        args.filter(a => a.isInstanceOf[FileObject] && a.otype != "")
          .map(arg => {
            val part1 =
                s"""
                  |         ${arg.name})
                  |            ${BashHelper.quoteSave(storeVariable, Seq("$1", "$(ViashAutodetectMount \"$2\")"))}
                  |            ${BashHelper.save(extraMountsVar, Seq("$(ViashAutodetectMountArg \"$2\")"))}
                  |            shift 2
                  |            ;;""".stripMargin
            val part2 =
              if (arg.otype == "--") {
                s"""
                  |         ${arg.name}=*)
                  |            ${BashHelper.quoteSave(storeVariable, Seq("$(ViashExtractFlags \"$1\"))", "$(ViashAutodetectMount \"$(ViashRemoveFlags \"$1\")\")"))}
                  |            ${BashHelper.save(extraMountsVar, Seq("$(ViashAutodetectMountArg \"$(ViashRemoveFlags \"$1\")\")"))}
                  |            shift 1
                  |            ;;""".stripMargin
              } else {
                ""
              }
            part1 + part2
          })
          .mkString("")
      }
   val parsers2 =
       s"""
          |         ---v|---volume)
          |            ${BashHelper.save(extraMountsVar, Seq("-v \"$2\""))}
          |            shift 2
          |            ;;
          |         ---volume=*)
          |            ${BashHelper.save(extraMountsVar, Seq("-v $(ViashRemoveFlags \"$2\")"))}
          |            shift 1
          |            ;;""".stripMargin

   val parsers = parsers1 + parsers2

   val extraParams = s" $$$extraMountsVar"

   val positional = args.filter(a => a.otype == "")
   val positionalStr = positional.zipWithIndex.map{tup =>
     val ix = tup._2 + 1
     if (tup._1.isInstanceOf[FileObject]) {
       s"""  ARG$ix="$$(ViashQuote "$$(ViashAutodetectMount "$$(echo $$$ix | sed "s#'##g")")")"""".stripMargin
     } else {
       s"""  ARG$ix="$$(ViashQuote "$$$ix")"""".stripMargin
     }
   }

   val postParse =
     if (positional.length > 0 && positional.exists(_.isInstanceOf[FileObject])) {
       s"""
       |function ViashDockerPostProcessPositionals {
       |${positionalStr.mkString("\n")}
       |  echo ${(1 to positional.length).map("$ARG" + _).mkString(" ")}
       |}
       |VIASHARGS=`ViashDockerPostProcessPositionals $$VIASHARGS`
       |""".stripMargin
     } else {
       ""
     }

   val inputs = Nil

   (preParse, parsers, postParse, inputs, extraParams)
  }

  def processDockerVolumesManual(functionality: Functionality) = {
    val storeVariable = functionality.mainScript match {
      case Some(e: Executable) => None
      case _ => Some("VIASHARGS")
    }

    val parsers =
      if (volumes.isEmpty) {
        ""
      } else {
        volumes.map(vol =>
          s"""
            |${BashHelper.argStore("--" + vol.name, vol.variable, "\"$2\"", 2, storeVariable)}
            |${BashHelper.argStoreSed("--" + vol.name, vol.variable, storeVariable)}"""
        ).mkString
      }

    val preParse = ""
    val postParse =
      if (volumes.isEmpty) {
        ""
      } else {
        volumes
          .map(vol =>
            s"""if [ -z $${${vol.variable}+x} ]; then
              |  ${vol.variable}=`pwd`; # todo: produce error here
              |fi""".stripMargin
          )
          .mkString("\n\n# provide temporary defaults for Docker\n", "\n", "")
      }

    val inputs = volumes.map(vol =>
      StringObject(
        name = "--" + vol.name,
        description = Some(s"Local path to mount directory for volume '${vol.name}'."),
        required = true,
        direction = Input
      )
    )

    val extraParams = volumes.map(vol => s""" -v "$$${vol.variable}":"${vol.mount}"""").mkString("")

    (preParse, parsers, postParse, inputs, extraParams)
  }

  def addDockerDebug(debugCommand: String) = {
    val preParse = ""
    val parsers = "\n" + BashHelper.argStore("---debug", "VIASHDEBUG", "yes", 1, None)
    val postParse =
      s"""
        |
        |# if desired, enter a debug session
        |if [ $${VIASHDEBUG} ]; then
        |  $debugCommand
        |  exit 0
        |fi"""

    val inputs = Nil

    (preParse, parsers, postParse, inputs)
  }
}

case class Volume(
  name: String,
  mount: String
) {
  private val VolumePattern = "^[A-Za-z_]*$".r

  require(
    VolumePattern.findFirstIn(name).isDefined,
    message = s"Volume $name: Should only consist of characters [A-Za-z_]."
  )

  val variable = "VOLUME_" + name.toUpperCase()
}
