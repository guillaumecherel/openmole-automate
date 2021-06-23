package omautom

import omautom.openmoleapi._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import sttp.client3.SttpBackend
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
        ZIO.accessM[Env](env => OpenMoleInstance.sendJob(job)
            .provideSome[Env](env => (env.om, env.httpBackend)))

    def logJobStatusUntilDone(job: JobId, sleep: Duration): ZIO[Env, Error, JobStatus] =
      val doSleep = ZIO.accessM[Env](env => ZIO.sleep(sleep).provide(env.clock))
      (logJobStatusOnce(job) <* doSleep).repeatUntil(_.isDone)

    def logJobStatusOnce(jobId: JobId): ZIO[Env, Error, JobStatus] =
        for
          status <- ZIO.accessM[Env](env => OpenMoleInstance.getJobStatus(jobId)
            .provide((env.om, env.httpBackend)))
          _ <- ZIO.accessM[Env](env => Logger.logMsg(
            s"Job ${jobId.toString}, ${status.toMessage}")
            .provide(env.logger))
        yield
            status

    def getJobResult(jobId: JobId, job: Job): ZIO[Env, Error, JobResult] =
      ZIO.accessM[Env](env => OpenMoleInstance.getJobResult(jobId, job)
        .provide((env.om, env.httpBackend)))

    def storeJobResult(job: Job, result: JobResult): ZIO[Env, Error, Unit] =
        ResultDatabase.storeJobResult(job, result)
