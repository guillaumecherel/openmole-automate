package omautom

import omautom.Error
import omautom.util._
import java.nio.file.{Path, Paths, Files}
import zio._

// scriptPath is relative to workindDir.
final case class Job(workingDir: Path, scriptPath: Path, outputDir: Path)

extension (job: Job)
  def pack: ZIO[Any, Error, Archive] =
    for 
      archivePath <- ZIO.effect(Files.createTempFile("openmole-job", ".tar.gz"))
        .mapError(thrown => Error.ArchiveFileCreationError(thrown))
      archive <- Archive.pack(archivePath, job.workingDir)
    yield archive
