package com.dataintuitive.viash.targets.environments

case class AptEnvironment(
  packages: List[String] = Nil
) {
  def getInstallCommands() = {
    val aptUpdate =
      """apt-get update"""

    val installPackages =
      packages match {
        case Nil => Nil
        case packs =>
          List(packs.mkString(
            "apt-get install -y ",
            " ",
            ""
          ))
      }

    val clean = "rm -rf /var/lib/apt/lists/*"

    aptUpdate :: installPackages ::: List(clean)
  }
}
