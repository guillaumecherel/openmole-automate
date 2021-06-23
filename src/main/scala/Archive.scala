package omautom.util

import org.apache.commons.compress.archivers._
import org.apache.commons.compress.archivers.tar._
import org.apache.commons.compress.compressors.gzip._
import org.apache.commons.compress.utils.IOUtils
import omautom.Logger
import omautom.Error
import java.io.BufferedInputStream
import java.nio.file._
import java.io.IOException
import zio._

final case class Archive(path: Path)

object Archive:
  def fromArchiveFile(path: Path): ZIO[Any, Error, Archive] = 
    for
      _ <- ZIO.ifM(
        ZIO.succeed(path.toString.endsWith(".tar.gz") || path.toString.endsWith(".tgz")))(
        onTrue = ZIO.succeed(Archive(path)),
        onFalse = ZIO.fail(Error.ArchiveFileNameInvalid(path,
          "TGZ file name should end with .tar.gz or .tgz")))
    yield
      Archive(path)

  def pack(archivePath: Path, sourceDir: Path): ZIO[Any, Error, Archive] = 
    tarGzOutputStream(archivePath).use { tarOut =>
      walkSubTree(sourceDir).foreach(packEntry)
      .provide(tarOut)
      .flatMap(_ => fromArchiveFile(archivePath))
    }

  def tarGzOutputStream(path: Path):
    ZManaged[Any, Error, TarArchiveOutputStream] =
    ZManaged.makeEffect(
      TarArchiveOutputStream(GzipCompressorOutputStream(
        Files.newOutputStream(path))))(
      tarOut =>
        tarOut.finish()
        tarOut.close()
      )
    .mapError(e => Error.ArchiveCreationError(path, e))

  def packEntry(path: Path): ZIO[TarArchiveOutputStream, Error, Unit] = 
    ZIO.accessM[TarArchiveOutputStream]( tarOut =>
      ZManaged.makeEffect(
        tarOut.createArchiveEntry(path.toFile, path.toString))(
        _ => tarOut.closeArchiveEntry())
      .use(entry =>
        for 
          _ <- ZIO.effect(tarOut.putArchiveEntry(entry))
          _ <- ZIO.when(
            Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))(
            ZManaged.fromAutoCloseable(ZIO.effect(Files.newInputStream(path)))
              .use(is => ZIO.effect(IOUtils.copy(is, tarOut))))
        yield
          ())
      .mapError(thrown => Error.ArchiveEntryCreationError(path, thrown)))

  def unpack(ar: Archive, targetDir: Path): ZIO[Any, Error, Unit] =
    tarGzInputStream(ar).use { tarInput => 
      unpackNextEntry(ar, targetDir).provide(tarInput).forever
        .catchSome { case _ : Error.ArchiveNoMoreEntry => ZIO.succeed(()) }
    }

  def tarGzInputStream(ar: Archive): 
    ZManaged[Any, Error, TarArchiveInputStream] =
    ZManaged.fromAutoCloseable(
      ZIO.effect(
        TarArchiveInputStream(
          GzipCompressorInputStream(
            BufferedInputStream(
              Files.newInputStream(ar.path)))))
      .mapError(e => Error.ArchiveOpeningError(ar.path, e)))

  def unpackNextEntry(ar: Archive, targetDir: Path):
    ZIO[TarArchiveInputStream, Error, Unit] =
    for
      tarInput <- ZIO.environment[TarArchiveInputStream]

      entry <- ZIO.effect(tarInput.getNextTarEntry())
        .mapError(thrown => Error.ArchiveCannotGetNextEntry(ar.path,
          Error.Thrown(thrown)))

      _ <- ZIO.when(entry == null)(ZIO.fail(Error.ArchiveNoMoreEntry(ar)))

      _ <- ZIO.effect(tarInput.canReadEntryData(entry))
        .mapError(thrown => Error.ArchiveCannotReadEntry(ar.path,
          entry.getName()))

      // TODO: Check that the absolute path falls inside targetDir
      // (relative paths starting with ../ could escape the subtree)
      entryAbsPath <- ZIO.effect(targetDir.resolve(entry.getName().stripPrefix("/")))
        .mapError(thrown => Error.InvalidFilePath(
          s"$targetDir/${entry.getName()}", Error.Thrown(thrown)))

      _ <- ZIO.debug(s"!!!DEBUG!!! $entryAbsPath")
      entryIsDir <- ZIO.effect(entry.isDirectory())
        .mapError(Error.Thrown.apply)

      _ <- if entryIsDir
        then

          ZIO.effect(Files.createDirectories(entryAbsPath))
          .refineToOrDie[IOException]
          .mapError(thrown => Error.CreateDirectoryFailed(entryAbsPath, thrown))

        else

          ZIO.effect(Files.createDirectories(entryAbsPath.getParent))
          .refineToOrDie[IOException]
          .mapError(thrown => Error.CreateDirectoryFailed(entryAbsPath, thrown))
          *>
          ZManaged.fromAutoCloseable(ZIO.effect(Files.newOutputStream(entryAbsPath)))
          .refineToOrDie[IOException]
          .mapError(thrown => Error.CreateOutputStreamFailed(entryAbsPath, thrown))
          .use(out => ZIO.effect(IOUtils.copy(tarInput, out))
            .refineToOrDie[IOException] 
            .mapError(thrown => 
              Error.ArchiveEntryExtractionError(ar.path, entry.getName(), thrown)))
    yield
      ()

extension (ar: Archive)
  def unpack(targetDir: Path): ZIO[Logger, Error, Unit] = 
    Archive.unpack(ar, targetDir)
