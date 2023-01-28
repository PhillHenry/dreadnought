package uk.co.odinconsultants.dreadnought.docker
import com.github.dockerjava.api.DockerClient
import uk.co.odinconsultants.dreadnought.Flow.Interpret

object Algebra {

  def toInterpret[T](client: DockerClient): Interpret[T] = CatsDocker.interpret(client, _)
  
}
