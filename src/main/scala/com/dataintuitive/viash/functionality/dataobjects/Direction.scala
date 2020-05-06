package com.dataintuitive.viash.functionality.dataobjects

sealed trait Direction
case object Input extends Direction
case object Output extends Direction
case object Log extends Direction