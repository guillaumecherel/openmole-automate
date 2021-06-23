package omautom

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








