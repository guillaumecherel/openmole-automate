package omautom

import java.nio.file.Path
import zio._

enum Authentication:
    case Egi(
      certificate: Path,
      vo: VirtualOrganisation)
    case Ssh(
      hostname: String,
      login: String,
      password: String)

opaque type VirtualOrganisation = String

object VirtualOrganisation:
    def apply(str: String): Either[Error, VirtualOrganisation] = fromString(str)

    def fromString(str: String): Either[Error, VirtualOrganisation] = 
        Right(str)
