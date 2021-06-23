package omautom

import omautom.util._
import omautom._
import java.io.IOException
import java.nio.file.Path
import scala.collection.immutable.List
import toml.Parse
import zio._

enum Error:
  case ArchiveCannotGetNextEntry(f: Path, cause: Error)
  case ArchiveCannotReadEntry(f: Path, entryName: String)
  case ArchiveCreationError(source: Path, cause: Throwable)
  case ArchiveFileCreationError(cause: Throwable)
  case ArchiveFileNameInvalid(filename: Path, info: String)
  case ArchiveEntryExtractionError(archive: Path, entry: String, cause: IOException)
  case ArchiveEntryCreationError(entry: Path, cause: Throwable)
  case ArchiveNoMoreEntry(archive: Archive)
  case ArchiveOpeningError(archive: Path, cause: Throwable)
  case ConfigFileError(f: Path, cause: Error)
  case ConfigFileMissingField(field: String)
  case ConfigFileReadError(f: Path, cause: Error)
  case ConfigFileTomlParseError(address: String, msg: String)
  case ConfigFileUnexpectedType(expected: String, fieldContent: String)
  case CreateDirectoryFailed(dir: Path, cause: IOException)
  case CreateOutputStreamFailed(path: Path, cause: IOException)
  case HttpClientCreationError(cause: Throwable)
  case HttpConnectionError(om: OpenMoleInstance, cause: Throwable)
  case HttpResponse(msg: String)
  case HttpResponseParseError(msg: String, cause: Option[Throwable])
  case HttpReadError(om: OpenMoleInstance, cause: Throwable)
  case InvalidFilePath(s: String, cause: Error)
  case List(list: scala.collection.immutable.List[Error])
  case Thrown(value: Throwable)
  case UnspecifiedError
  case WalkingSubTreeError(root: Path, cause: Error)

extension (e: Error)
  def msg: String = e.toString ++ "\n" ++ (
    e match 
      case Error.ArchiveCannotGetNextEntry(path, cause) =>
        s"Cannot read next entry from archive $path: ${cause.msg}"
      case Error.ArchiveCannotReadEntry(path, entryName) =>
        s"Cannot read entry $entryName from archive $path"
      case Error.ArchiveCreationError(source, cause) =>
        s"Cannot create archive from subtree ${source}, cause: ${cause.toString}"
      case Error.ArchiveFileCreationError(cause) =>
        s"Cannot create archive file, cause: ${cause.toString}"
      case Error.ArchiveEntryExtractionError(archive, entry, cause) =>
        s"Could not extract entry $entry from archive $archive, cause: ${cause.toString}"
      case Error.ArchiveEntryCreationError(entry, cause) =>
        s"Could not create archive entry $entry, cause: ${e.toString}"
      case Error.ArchiveNoMoreEntry(archive) =>
        s"No more entry to extract from archive ${archive.path}"
      case Error.ArchiveOpeningError(archive, cause) =>
        s"Could not open archive $archive, cause: ${cause.toString}"
      case Error.ConfigFileError(f, cause) =>
        s"Error with Config file $f, cause: ${cause.msg}"
      case Error.ConfigFileMissingField(field) =>
        s"Config file is missing the field $field"
      case Error.ConfigFileReadError(f, cause) =>
        s"cannot read config file $f, cause: ${cause.msg}"
      case Error.ConfigFileTomlParseError(address, msg) =>
        s"cannot parse TOML at $address, $msg"
      case Error.ConfigFileUnexpectedType(expected, fieldContent) =>
        s"TOML value does not have the expected type $expected: $fieldContent"
      case Error.CreateDirectoryFailed(dir, cause) =>
        s"Could not create directory $dir, cause: ${cause.toString}"
      case Error.CreateOutputStreamFailed(path, cause) =>
        s"Could not create output stream to $path, cause: ${cause.toString}"
      case Error.HttpClientCreationError(cause) =>
        s"Could not create HTTP client, cause: ${cause.toString}"
      case Error.HttpConnectionError(om, cause) =>
        s"Could not create HTTP connection to ${om.toString}, cause: ${cause.toString}"
      case Error.HttpReadError(om, cause) =>
        s"There was a problem reading the HTTP response from ${om.toString}, cause: ${cause.toString}"
      case Error.HttpResponse(msg) =>
        s"Http request is unsuccessful : $msg"
      case Error.InvalidFilePath(s, cause) =>
        s"File Path $s is invalid, cause: ${cause.msg}"
      case Error.List(list) =>
        list.mkString(", ")
      case Error.Thrown(value) =>
        s"Thrown error: ${value.toString}"
      case Error.UnspecifiedError =>
        s"Unspecified error"
      case Error.WalkingSubTreeError(root, cause) =>
        s"Error walking sub tree from $root, cause: ${cause.toString}"
      case _ => "")
