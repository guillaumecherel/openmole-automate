package omautom.openmoleapi

import com.fasterxml.jackson.databind._
import omautom.Error
import omautom.util.Archive
import omautom.util.pathFromString
import java.nio.file.{Path, Paths, Files}
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio._



opaque type JobId = String

object JobId:
  def apply(str: String): JobId = str



final case class OpenMoleInstance(
    address: String,
    port: String)



extension (om: OpenMoleInstance)
  def url: String = s"http://${om.address}:${om.port}"



object OpenMoleInstance:

  def sendJob(job: Job):
    ZIO[(OpenMoleInstance, SttpBackend[Task, _]), Error, JobId] =
    for
      archivePath <- ZIO.effect(Files.createTempFile("openmole-job", ".tar.gz"))
        .mapError(thrown => Error.ArchiveFileCreationError(thrown))
      archive <- Archive.pack(archivePath, job.workingDir)
      jobId <- ZIO.accessM[(OpenMoleInstance, SttpBackend[Task, _])](
        (om, _) => sendRequest(Request.postJob(om, archive, job.workingDir.resolve(job.scriptPath))))
    yield
      jobId

  def getJobStatus(jobId: JobId):
    ZIO[(OpenMoleInstance, SttpBackend[Task, _]), Error, JobStatus] =
    ZIO.accessM[(OpenMoleInstance, SttpBackend[Task, _])](
      (om, _) => sendRequest(Request.getJobStatus(om, jobId)))

  def getJobResult(jobId: JobId, job: Job):
    ZIO[(OpenMoleInstance, SttpBackend[Task, _]), Error, JobResult] =
    for
      archivePath <- ZIO.accessM[(OpenMoleInstance, SttpBackend[Task, _])](
        (om, _) => sendRequest(Request.getJobResult(om, jobId, job.outputDir)))
      ar <- Archive.fromArchiveFile(archivePath)
      _ <- Archive.unpack(ar, job.outputDir)
    yield
      JobResult(ar)

  def sendRequest[T](request: Request[Either[Error, T], Any]):
    ZIO[(OpenMoleInstance, SttpBackend[Task, _]), Error, T]=
    ZIO.accessM[(OpenMoleInstance, SttpBackend[Task, _])](
      (om, sttp) => request.send(sttp))
    //.debug(s"!!!DEBUG!!! HTTP REQUEST: ${request.toString}\n!!!DEBUG!!! HTTP RESPONSE: ")
    .catchAll(_ match 
        case e: SttpClientException.ConnectException => 
          ZIO.accessM[(OpenMoleInstance, SttpBackend[Task, _])]((om, _) => 
            ZIO.fail(Error.HttpConnectionError(om, e)))
        case e: SttpClientException.ReadException => 
          ZIO.accessM[(OpenMoleInstance, SttpBackend[Task, _])]((om, _) => 
            ZIO.fail(Error.HttpReadError(om, e))))
    .map(response => response.body)
    .absolve



object Request:

  //TODO: cet objet devrait être passé comme environnement ZIO pour que les
  //fonctions ci-dessous y ait accès sans effet de bord. Ça implique qu'elles
  //renvoient un ZIO.
  val objectMapper = ObjectMapper()

  def postJob(om: OpenMoleInstance, jobArchive: Archive, scriptPath: Path):
    Request[Either[Error, JobId], Any] =
    basicRequest
    .post(uri"${om.url}/job")
    .multipartBody(
      multipartFile("workDirectory", jobArchive.path),
      multipart("script", scriptPath.toString))
    .response(asEither(
      onError = asStringAlways.map(resp => Error.HttpResponse(resp)),
      onSuccess = asStringAlways.map(json => 
        for
          jobId <- getJsonFieldAsString("id", json)
        yield
          JobId(jobId)))
      .map(_.flatten))

  def getJobStatus(om: OpenMoleInstance, jobId: JobId):
    Request[Either[Error, JobStatus], Any] =
    basicRequest
    .get(uri"${om.url}/job/${jobId.toString}/state")
    .response(
      asEither(
        onError = asStringAlways.map(resp => Error.HttpResponse(resp)),
        onSuccess = asStringAlways.map(json =>
          for
            state <- getJsonFieldAsString("state", json)
            jobStatus <- state match
              case "running" =>
                for
                  ready <- getJsonFieldAsString("ready", json)
                  running <- getJsonFieldAsString("running", json)
                  completed <- getJsonFieldAsString("completed", json)
                  environments <- getJsonFieldAsString("environments", json)
                yield
                  JobStatus.Running(ready.toInt, running.toInt, completed.toInt,
                    environments)
              case "finished" =>
                Right(JobStatus.Finished)
              case "failed" =>
                for
                  errors <- getJsonFieldAsString("errors", json)
                  stackTrace <- getJsonFieldAsString("stack", json)
                yield
                  JobStatus.Failed(errors, stackTrace)
              case other =>
                Left(Error.HttpResponseParseError(s"OpenMole Rest API answer contains"
                  + " the unexpected value ${other} for the field 'ready'.", Option.empty))
          yield
            jobStatus))
      .map(_.flatten))

  def getJobOutput(om: OpenMoleInstance, jobId: JobId):
    Request[Either[Error, String], Any] =
    basicRequest
    .get(uri"${om.url}/job/${jobId.toString}/output")
    .response(asEither(
      onError = asStringAlways.map(resp => Error.HttpResponse(resp)),
      onSuccess = asStringAlways))

  def getJobResult(om: OpenMoleInstance, jobId: JobId, jobOutputDir: Path):
    Request[Either[Error, Path], Any] =
    basicRequest
    .get(uri"${om.url}/job/${jobId}/workDirectory/${jobOutputDir.toString}")
    .response(asEither(
      onError = asStringAlways.map(resp => Error.HttpResponse(resp)),
      // TODO: Que se passe-t-il si Paths.get produit une erreur? Il faudrait
      // pouvoir faire passer l'erreur dans le type Error de retour
      onSuccess = asPathAlways(Files.createTempFile(
        s"openmole-job-result-$jobId", ".tar.gz"))))

  def getJsonField(field: String, json: String):
    Either[Error, JsonNode] =
    val jsonNode : JsonNode = 
      try
        objectMapper.readTree(json)
      catch
        case e: Exception => 
          return Left(Error.HttpResponseParseError(
            s"Could not read JSON tree: json.toString", Option(e)))

    val value = jsonNode.get(field)
    Either.cond(value != null,
      right = value,
      left = Error.HttpResponseParseError(
        s"OpenMole Rest API answer is missing the field '$field' in : $json",
        Option.empty))

  def getJsonFieldAsString(field: String, json: String): Either[Error, String] =
    getJsonField(field, json).map(_.asText)
