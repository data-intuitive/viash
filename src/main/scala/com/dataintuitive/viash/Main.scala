package com.dataintuitive.viash

import functionality._
import targets._

import java.nio.file.{Paths, Files}
import scala.io.Source

import java.nio.charset.StandardCharsets

object Main {
  def main(args: Array[String]) {
    val conf = new CLIConf(args)
    
    val funcPath = new java.io.File(conf.functionality())
    val targPath = new java.io.File(conf.platform())
    
    println("Parsing functionality")
    val functionality = Functionality.parse(funcPath)
    
    println(functionality.platform.generateArgparse(functionality))
    
    println("Parsing target")
    val target = Target.parse(targPath)
    
    println("Processing resources")
    val modifiedFunctionality = target.modifyFunctionality(functionality, funcPath)
//    val resources = 
//      functionality.resources.toList ::: 
//      target.setupResources(functionality).toList
    
    println("Writing resources")
    conf.subcommand match {
      case Some(conf.run) => {
        val dir = Files.createTempDirectory("viash_" + modifiedFunctionality.name).toFile()
        writeResources(modifiedFunctionality.resources, funcPath, dir)
      }
      case Some(conf.export) => {
        val dir = new java.io.File(conf.export.output())
        dir.mkdirs()
        writeResources(modifiedFunctionality.resources, funcPath, dir)
      }
      case Some(_) => println("??")
      case None => println("No subcommand was specified")
    }
  }
  
  def writeResources(
    resources: Seq[Resource],
    inputDir: java.io.File,
    outputDir: java.io.File, 
    overwrite: Boolean = true
  ) {
    // copy all files
    resources.foreach(
      resource => {
        val dest = Paths.get(outputDir.getAbsolutePath, resource.name)
        
        val destFile = dest.toFile()
        if (overwrite && destFile.exists()) {
          destFile.delete()
        }
        
        if (resource.path.isDefined) {
          val sour = Paths.get(inputDir.getParent(), resource.path.get)
          Files.copy(sour, dest)
        } else {
          val code = resource.code.get
          Files.write(dest, code.getBytes(StandardCharsets.UTF_8))
        }
        
        if (resource.executable.isDefined) {
          destFile.setExecutable(resource.executable.get)
        }
      }
    )
  }
  
  def run(
      
  ) = {
//      import sys.process._
//      
//      command !
  }
}
