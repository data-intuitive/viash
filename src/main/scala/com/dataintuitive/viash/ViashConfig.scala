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

package com.dataintuitive.viash

import com.dataintuitive.viash.config.{Config, encodeConfig}
import com.dataintuitive.viash.dsl.DSLCommand
import io.circe.yaml.Printer

object ViashConfig {
  val printer = Printer(
    preserveOrder = true,
    dropNullKeys = true,
    mappingStyle = Printer.FlowStyle.Block,
    splitLines = true,
    stringStyle = Printer.StringStyle.DoubleQuoted
  )

  def view(config: Config, commands: List[String])  {
    val newConf = commands.foldLeft(config)((config, command) => DSLCommand.apply(config, command))
    val json = encodeConfig(newConf)
    val configYamlStr = printer.pretty(json)
    println(configYamlStr)
  }
}