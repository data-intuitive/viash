package com.dataintuitive.viash.targets

sealed trait ResolveVolume
case object Manual extends ResolveVolume
case object Automatic extends ResolveVolume
