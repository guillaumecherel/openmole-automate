package omautom.openmoleapi

enum JobStatus:
  case Running(ready: Int, running: Int, completed: Int, environments: String)
  case Finished
  case Failed(msg: String, stackTrace: String)

extension (s: JobStatus)
    def isDone: Boolean = s match 
      case JobStatus.Running(_, _, _, _) => false
      case JobStatus.Finished => true
      case JobStatus.Failed(_, _) => true

    def toMessage: String = s match
      case JobStatus.Running(ready, running, completed, environments) => 
        s"Job is running, $ready ready, $running running, $completed completed. Environments info: $environments."
      case JobStatus.Finished => 
        s"Job done."
      case JobStatus.Failed(msg, stackTrace) => 
        s"Job failed with message: $msg. StackTrace: $stackTrace"



final case class JobEnvironment(
  name: Option[String],
  submitted: Int,
  running: Int,
  done: Int,
  failed: Int,
  errors: Vector[JobError])

final case class JobError(msg: String, staceTrace: String, level: String)

