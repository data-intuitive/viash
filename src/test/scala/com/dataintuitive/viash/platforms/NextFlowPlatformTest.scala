package com.dataintuitive.viash.platforms

import org.scalatest.FunSuite
import scala.util.Try
import com.dataintuitive.viash.platforms.NextFlowUtils._

import com.dataintuitive.viash.config.Config
import com.dataintuitive.viash.functionality.Functionality

class NextFlowPlatformTest extends FunSuite {

  val simpleTuple1:ConfigTuple = ("key", "value")
  val simpleTuple2:ConfigTuple = ("key", true)
  val simpleTuple3:ConfigTuple = ("key", 2)

  val listTuple:ConfigTuple = ("key", List("value1", "value2"))

  val nestedTuple:ConfigTuple = ("key", NestedValue(List(simpleTuple1, simpleTuple2, simpleTuple3, listTuple)))

  // convert testbash
  test("NextFlowPlatform can deal with simple and nested Tuples") {
    assert(simpleTuple1.isInstanceOf[ConfigTuple])
    assert(nestedTuple.isInstanceOf[ConfigTuple])
  }

  test("Tuples can be implicitly converted to ConfigTuples for plain values") {
    assert(Try(("key", "values").toConfig()).toOption.isDefined)
  }

  test("ConfigTuples can be exported to config String") {
    assert(simpleTuple1.toConfig("") === """key = "value"""")
    assert(simpleTuple2.toConfig("") === """key = true""")
    assert(simpleTuple3.toConfig("") === """key = 2""")
  }

  test("Nested ConfigTuples can be exported to String properly as well") {
    val configString = nestedTuple.toConfig("  ")
    val expectedString =
      """  key {
        |    key = "value"
        |    key = true
        |    key = 2
        |    key = [ "value1", "value2" ]
        |  }"""
      .stripMargin
    assert(configString === expectedString)
  }
}
