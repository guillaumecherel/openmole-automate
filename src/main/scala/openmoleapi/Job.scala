package omautom.openmoleapi

import java.nio.file.{Path, Paths, Files}
import zio._

// scriptPath is relative to workindDir.
final case class Job(workingDir: Path, scriptPath: Path, outputDir: Path)
