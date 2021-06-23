package omautom

import java.io.IOException
import java.nio.file.{Files, Path, Paths}
import omautom.openmoleapi._
import omautom.util
import toml.Toml
import toml.Value
import zio._
import zio.blocking._

final case class Options private (
    openMoleInstance: OpenMoleInstance,
    authentications: List[Authentication],
    job: Job)

object Options:
  def fromFilePath(filePath: Path): ZIO[Blocking, Error, Options] = 
    for
      configStr <- readConfigFile(filePath)
      opts <- fromString(configStr)
        .mapError(e => Error.ConfigFileReadError(filePath, e))
    yield opts

  def fromFilePathString(filePath: String): ZIO[Blocking, Error, Options] =
    for
      optsFilePath <- ZIO.effect(Paths.get(filePath))
        .mapError[Error](e => Error.InvalidFilePath(filePath,
        Error.Thrown(e)))
      opts <- fromFilePath(optsFilePath)
    yield opts

  def readConfigFile(filePath: Path): ZIO[Blocking, Error, String] =
    ZIO.effect(Files.readString(filePath))
      .mapError(e => Error.ConfigFileReadError(filePath, Error.Thrown(e)))

  def fromString(str: String): IO[Error, Options] =
    for
      optsTbl <- ZIO.fromEither(Toml.parse(str)
        .left.map((address, msg) => Error.ConfigFileTomlParseError(address.mkString(","), msg)))
      opts <- Options.fromTomlRoot(optsTbl)
    yield opts

  def fromTomlRoot(toml: Value): IO[Error, Options] =
    for
      openMoleInstance <- tomlOpenMoleInstance(toml)
      auths <- tomlAuths(toml)
      job <- tomlJob(toml)
    yield
      Options(openMoleInstance, auths, job)

  def tomlTableGet(toml: Value, field: String): IO[Error, Value] =
     toml match
      case Value.Tbl(values) => ZIO.fromEither(values.get(field)
        .toRight(Error.ConfigFileMissingField(field)))
      case _ => ZIO.fail(Error.ConfigFileUnexpectedType("table", toml.toString()))

  def tomlList(toml: Value): IO[Error, List[Value]] =
     toml match
      case Value.Arr(values) => ZIO.effect(values).mapError(Error.Thrown.apply)
      case _ => ZIO.fail(Error.ConfigFileUnexpectedType("list", toml.toString()))

  def tomlString(toml: Value): IO[Error, String] =
     toml match
      case Value.Str(value) => ZIO.effect(value).mapError(Error.Thrown.apply)
      case _ => ZIO.fail(Error.ConfigFileUnexpectedType("string", toml.toString()))

  def tomlJob(toml: Value): IO[Error, Job] = 
    for
      jobTbl <- tomlTableGet(toml, "Job")
      rootDir <- tomlTableGet(jobTbl, "root")
        .flatMap(tomlString)
        .flatMap(util.pathFromString)
      scriptPath <- tomlTableGet(jobTbl, "script")
        .flatMap(tomlString)
        .flatMap(util.pathFromString)
      outputDir <- tomlTableGet(jobTbl, "output")
        .flatMap(tomlString)
        .flatMap(util.pathFromString)
    yield
       Job(rootDir, scriptPath, outputDir)

  def tomlAuths(toml: Value): IO[Error, List[Authentication]] =
    for
      egiAuthTblList <- tomlTableGet(toml, "EgiAuthentication")
        .flatMap(tomlList)
        .catchSome(_ match
          case _: Error.ConfigFileMissingField => ZIO.succeed(List.empty))
      egiAuths <- ZIO.foldLeft[Any, Error, List[Authentication], Value](
        egiAuthTblList)(List.empty)((acc, value) => 
        tomlEgiAuth(value).map(auth => auth :: acc))
      sshAuthTblList <- tomlTableGet(toml, "SshAuthentication")
        .flatMap(tomlList)
        .catchSome(_ match
          case _: Error.ConfigFileMissingField => ZIO.succeed(List.empty))
      sshAuths <- ZIO.foldLeft[Any, Error, List[Authentication], Value](
        sshAuthTblList)(List.empty)((acc, value) => 
        tomlSshAuth(value).map(auth => auth :: acc))
    yield
      egiAuths ::: sshAuths

  def tomlEgiAuth(toml: Value): IO[Error, Authentication] =
    for
      cert <- tomlTableGet(toml, "certificate")
        .flatMap(tomlString)
        .flatMap(util.pathFromString)
      vo <- tomlTableGet(toml, "vo")
        .flatMap(tomlString)
        .flatMap(str => ZIO.fromEither(VirtualOrganisation.fromString(str)))
    yield
      Authentication.Egi(cert, vo)

  def tomlSshAuth(toml: Value): IO[Error, Authentication] =
    for
      hostname <- tomlTableGet(toml, "hostname")
        .flatMap(tomlString)
      login <- tomlTableGet(toml, "login")
        .flatMap(tomlString)
      password <- tomlTableGet(toml, "password")
        .flatMap(tomlString)
    yield
      Authentication.Ssh(hostname, login, password)

  def tomlOpenMoleInstance(toml: Value): IO[Error, OpenMoleInstance] =
    for
      openMoleTbl <- tomlTableGet(toml, "OpenMole")
      openMoleAddress <- tomlTableGet(openMoleTbl, "address")
        .flatMap(tomlString)
      openMolePort <- tomlTableGet(openMoleTbl, "port")
        .flatMap(tomlString)
    yield
      OpenMoleInstance(openMoleAddress, openMolePort)
