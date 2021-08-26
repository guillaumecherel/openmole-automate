package omautom

import com.fasterxml.jackson.databind._
import java.nio.file.{Path, Paths, Files}
import omautom._
import omautom.util._
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio._

object Request:

  //TODO: cet objet devrait être passé comme environnement ZIO pour que les
  //fonctions ci-dessous y ait accès sans effet de bord. Ça implique qu'elles
  //renvoient un ZIO.
  val objectMapper = ObjectMapper()

  def send[T](om: OpenMoleInstance, request: Request[Either[Error, T], Any]):
    ZIO[SttpBackend[Task, _], Error, T]=
    ZIO.accessM[SttpBackend[Task, _]](sttp => request.send(sttp))
    .catchAll(_ match 
      case e: SttpClientException.ConnectException => 
        ZIO.fail(Error.HttpConnectionError(om, e))
      case e: SttpClientException.ReadException => 
        ZIO.fail(Error.HttpReadError(om, e)))
    .map(response => response.body)
    .absolve


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
          jsonNode <- getJsonNodeFromString(json)
          jobId <- getJsonField("id", jsonNode)
        yield
          JobId(jobId.asText)))
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
            jsonNode <- getJsonNodeFromString(json)
            state <- getJsonField("state", jsonNode).map(_.asText)
            jobStatus <- state match
              case "running" =>
                for
                  ready <- getJsonField("ready", jsonNode).map(_.asText)
                  running <- getJsonField("running", jsonNode).map(_.asText)
                  completed <- getJsonField("completed", jsonNode).map(_.asText)
                  environments <- getJsonField("environments", jsonNode).map(_.asText)
                yield
                  JobStatus.Running(ready.toInt, running.toInt, completed.toInt,
                    environments)
              case "finished" =>
                Right(JobStatus.Finished)
              case "failed" =>
                for
                  error <- getJsonField("error", jsonNode)
                  message <- getJsonField("message", error)
                  stackTrace <- getJsonField("stackTrace", error)
                yield
                  JobStatus.Failed(message.asText, stackTrace.asText)
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

  def getJsonField(field: String, jsonNode: JsonNode):
    Either[Error, JsonNode] =
    val value = jsonNode.get(field)
    Either.cond(value != null,
      right = value,
      left = Error.HttpResponseParseError(
        s"OpenMole Rest API answer is missing the field '$field' in : ${jsonNode.asText}",
        Option.empty))

  def getJsonNodeFromString(json: String):
    Either[Error, JsonNode] =
      try
        Right(objectMapper.readTree(json))
      catch
        case e: Exception => 
           Left(Error.HttpResponseParseError(
            s"Could not read JSON tree: json.toString", Option(e)))

