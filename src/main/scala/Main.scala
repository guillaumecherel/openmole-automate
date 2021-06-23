package omautom;

import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio._
import zio.blocking._
import zio.console._
import zio.clock._
import zio.duration._

import scala.collection.View.Empty

val defaultOptionsPath: String = ".openmole-automate.toml"

@main def openmoleAutomate: Unit =

    def env(opts: Options): ZIO[Has[Clock.Service] & Has[Blocking.Service] & Has[Console.Service], Error, Env] =
      for
        httpBackend <- HttpClientZioBackend()
          .mapError(thrown => Error.HttpClientCreationError(thrown))
        zioConsole <- ZIO.environment[Has[Console.Service]]
        blocking <- ZIO.environment[Has[Blocking.Service]]
        clock <- ZIO.environment[Has[Clock.Service]]
      yield
        Env(
          opts.openMoleInstance,
          Logger.Console(zioConsole),
          blocking,
          httpBackend,
          clock)

    def logic(opts: Options): ZIO[Env, Error, Unit] =
      for
        jobId <- Env.sendOpenMoleJob(opts.job)
        _ <- Env.logJobStatusUntilDone(jobId, 100.millisecond)
        jobResult <- Env.getJobResult(jobId, opts.job)
      yield
        ()

    def main: ZIO[Has[Clock.Service] & Has[Blocking.Service] & Has[Console.Service], RuntimeException, Unit] = 
      for
        opts <- Options.fromFilePathString(defaultOptionsPath)
          .mapError(e => RuntimeException(e.msg))
        env <- env(opts)
          .mapError(e => RuntimeException(e.msg))
        _ <- logic(opts)
          .mapError(e => RuntimeException(e.msg))
          .provide(env)
      yield
        ()

    Runtime.default.unsafeRun(main.exitCode)

