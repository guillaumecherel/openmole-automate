package omautom

import java.nio.file.{Path, Paths, Files}
import omautom._
import omautom.util._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.client3.SttpBackend
import sttp.client3._
import zio._
import zio.duration._

// HttpClientZioBackend() has type Task[SttpBackend[Task, _]]

final case class Env(
    om: OpenMoleInstance,
    logger: Logger,
    blockingThreadPool: zio.blocking.Blocking,
    httpBackend: SttpBackend[Task, _],
    clock: zio.clock.Clock)

object Env:

  def sendOpenMoleJob(job: Job): ZIO[Env, Error, JobId] =
    for 
      archive <- job.pack
      jobId <- ZIO.accessM[Env](env =>
        Request.send(
          env.om,
          Request.postJob(env.om, archive,
            job.workingDir.resolve(job.scriptPath)))
        .provide(env.httpBackend))
    yield
      jobId

  def logJobStatusUntilDone(job: JobId, sleep: Duration): ZIO[Env, Error, JobStatus] =
    val doSleep = ZIO.accessM[Env](env => ZIO.sleep(sleep).provide(env.clock))
    (logJobStatusOnce(job) <* doSleep).repeatUntil(_.isDone)

  def logJobStatusOnce(jobId: JobId): ZIO[Env, Error, JobStatus] =
      for
        status <- getJobStatus(jobId)
        _ <- ZIO.accessM[Env](env => Logger.logMsg(
          s"Job ${jobId.toString}, ${status.toMessage}")
          .provide(env.logger))
      yield
          status

  def getJobStatus(jobId: JobId): ZIO[Env, Error, JobStatus] =
    ZIO.accessM[Env](env => 
      Request.send(env.om, Request.getJobStatus(env.om, jobId))
      .provide(env.httpBackend))

  def getJobResult(jobId: JobId, job: Job): ZIO[Env, Error, JobResult] =
    for
      archivePath <- ZIO.accessM[Env]( env =>
        Request.send(
          env.om,
          Request.getJobResult(env.om, jobId, job.outputDir))
        .provide(env.httpBackend))
      ar <- Archive.fromArchiveFile(archivePath)
      _ <- Archive.unpack(ar, job.outputDir)
    yield
      JobResult(ar)

