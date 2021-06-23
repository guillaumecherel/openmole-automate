package omautom;

import omautom.Error
import zio._
import zio.console._

enum Logger:
  case Console(zioConsole: zio.console.Console)

object Logger:
    def logMsg(str: String):
      ZIO[Logger, Error, Unit] = 
      ZIO.accessM[Logger](logger => logger match
        case Logger.Console(zioConsole) =>
          putStrLn(str).provide(zioConsole).mapError(thrown =>
            Error.Thrown(thrown)))

    def logError(e: Error): ZIO[Logger, Error, Unit] = logMsg(e.toString)

