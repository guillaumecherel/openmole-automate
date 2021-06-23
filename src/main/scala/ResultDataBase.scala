package omautom;

import java.nio.file.{Path, Paths}
import omautom.openmoleapi._
import omautom.util._
import zio._

// TODO Supprimer cette classe

final val RESULT_DB_PATH: String = "/srv/openmole-continuous-modelling"

object ResultDatabase:
  def rootPath(): ZIO[Any, Error, Path] = util.pathFromString(RESULT_DB_PATH)

  def storeJobResult(job: Job, result: JobResult): ZIO[Env, Error, Unit] =
    for
      dbRoot <- ResultDatabase.rootPath()
      resultRoot = dbRoot.resolve(job.outputDir)
      _ <- result.archive.unpack(resultRoot).provideSome[Env](_.logger)
    yield
      ()

