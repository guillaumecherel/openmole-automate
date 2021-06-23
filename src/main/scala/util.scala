package omautom.util

import omautom.Error
import java.nio.file.{Files, Path, Paths}
import scala.jdk.StreamConverters._
import zio._
import zio.stream._

def pathFromString(str: String): ZIO[Any, Error, Path] =
  ZIO.effect(Paths.get(str))
  .mapError(e => Error.InvalidFilePath(str, Error.Thrown(e)))

def walkSubTree(start: Path): ZStream[Any, Error, Path] =
  ZStream.fromJavaStreamManaged(ZManaged.fromAutoCloseable(ZIO.effect(Files.walk(start))))
  .mapError(thrown => Error.WalkingSubTreeError(start, Error.Thrown(thrown)))
