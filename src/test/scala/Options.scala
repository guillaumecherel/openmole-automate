import omautom._
import omautom.openmoleapi._
import org.scalatest._
import org.scalatest.EitherValues.convertEitherToValuable
import org.scalatest.flatspec._
import org.scalatest.matchers._
import org.scalatest.Inside._
import zio._

import java.nio.file.Paths

class OptionsSpecs extends AnyFlatSpec with should.Matchers:
    "Options" should "be successfully constructed from a simple toml example." in {

        val toml = """
            | [Job]
            | root = "./testjob"
            | script = "script.oms"
            | output = "results/"
            |
            | [OpenMole]
            | address = "localhost"
            | port = "8080"
            |
            | [[EgiAuthentication]]
            | certificate = "path/to/cert.p12"
            | vo = "vo.complex-systems.eu"
            |
            | [[SshAuthentication]]
            | hostname = "host1"
            | login = "user1"
            | password = "pass1"
            |
            | [[SshAuthentication]]
            | hostname = "host2"
            | login = "user2"
            | password = "pass2"
        """.stripMargin

        val ocmOpts = Runtime.default.unsafeRun(Options.fromString(toml))

        ocmOpts.openMoleInstance.address should be ("localhost")
        ocmOpts.openMoleInstance.port should be ("8080")
        inside (ocmOpts.authentications(0)) {
            case Authentication.Egi(cert, vo) => 
                cert should be (Paths.get("path/to/cert.p12"))
                vo should be ("vo.complex-systems.eu")
        }
        inside (ocmOpts.authentications(2)) {
            case Authentication.Ssh(hostname, login, password) => 
                hostname should be ("host1")
                login should be ("user1")
                password should be ("pass1")
        }
        inside (ocmOpts.authentications(1)) {
            case Authentication.Ssh(hostname, login, password) => 
                hostname should be ("host2")
                login should be ("user2")
                password should be ("pass2")
        }
        inside (ocmOpts.job) {
            case Job(root, script, output) => 
                root should be (Paths.get("./testjob"))
                script should be (Paths.get("script.oms"))
                output should be (Paths.get("results/"))
        }
    }


