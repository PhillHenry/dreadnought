package uk.co.odinconsultants.dreadnought.docker

import cats.arrow.FunctionK
import cats.effect.{ExitCode, IO, IOApp}
import cats.effect.std.{Dispatcher, Queue}
import cats.free.Free
import fs2.Stream
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.{CreateContainerCmd, CreateContainerResponse}
import com.github.dockerjava.api.model.{ExposedPort, Frame, Link, Ports}
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientImpl}
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import uk.co.odinconsultants.dreadnought.docker.RawDocker.*

object CatsDocker {

  opaque type ApiVersion = String
  opaque type Port       = Int

  def initializeClient(dockerHost: String, apiVersion: ApiVersion): IO[DockerClient] = IO {
    val config: DefaultDockerClientConfig  = buildConfig(dockerHost, apiVersion)
    val httpClient: ApacheDockerHttpClient = buildClient(config)
    DockerClientImpl.getInstance(config, httpClient)
  }

  def stopContainer(client: DockerClient, containerId: String): IO[Unit] = IO {
    stopContainerWithId(client, containerId)
  }

  val client: IO[DockerClient] = {
    val host       = "unix:///var/run/docker.sock"
    val apiVersion = "1.41"
    initializeClient(host, apiVersion).handleErrorWith { (t: Throwable) =>
      IO.println(s"Could not connect to host $host using API version $apiVersion") *>
        IO.raiseError(t)
    }
  }

  def interpret[T](client: DockerClient, tree: Free[ManagerRequest, T]): IO[T] =
    val requestToIO: FunctionK[ManagerRequest, IO] = new FunctionK[ManagerRequest, IO] {
      def apply[A](l: ManagerRequest[A]): IO[A] = interpreter[A](client)(l)
    }
    tree.foldMap(requestToIO)

  def interpreter[A](client: DockerClient): ManagerRequest[A] => IO[A] = {
    case StartRequest(image, cmd, env, ports, dns, name) =>
      createAndStart(client, image, cmd, env, ports, dns, name)
    case StopRequest(containerId)                        => stopContainer(client, containerId.toString)
    case NamesRequest(containerId)                       =>
      IO(listContainers(client).filter(_.getId == containerId.toString).flatMap(_.getNames))
    case LoggingRequest(containerId, cb)                 => loggingContainer(client, containerId, cb).start
  }

  def loggingContainer(
      client:      DockerClient,
      containerId: ContainerId,
      cb:          String => IO[Unit],
  ): IO[Unit] = {
    val x: Stream[IO, Unit] = for {
      dispatcher <- Stream.resource(Dispatcher[IO].onFinalize(IO.println("Releasing Dispatcher")))
      q          <- Stream.eval(Queue.unbounded[IO, String])
      _          <- Stream.eval(IO.delay {
                      def report(msg: String): Unit =
                        dispatcher.unsafeRunAndForget(q.offer(s"$containerId $msg"))
                      log(client, containerId.toString, report)
                    })
      _          <- Stream.eval(q.take.flatMap(cb)).repeat
    } yield ()
    x.compile.drain
  }

  private def createAndStart(
      dockerClient: DockerClient,
      image:        ImageName,
      command:      Command,
      environment:  Environment,
      portMappings: NetworkMapping[Port],
      dnsMappings:  DnsMapping[String],
      name:         Option[String],
  ): IO[ContainerId] = for {
    container <-
      createContainer(dockerClient, image, command, environment, portMappings, dnsMappings, name)
    id        <- start(dockerClient, container)
  } yield id

  /**
   * If you use a name, a container with that name might already exist.
   * Therefore, TODO: delete the old container.
   * See https://stackoverflow.com/questions/31697828/docker-name-is-already-in-use-by-container
   */
  private def createContainer(
      dockerClient: DockerClient,
      image:        ImageName,
      command:      Command,
      environment:  Environment,
      portMappings: NetworkMapping[Port],
      dnsMappings:  DnsMapping[ApiVersion],
      name:         Option[String],
  ): IO[CreateContainerResponse] = IO {
    import scala.jdk.CollectionConverters.*

    val config: CreateContainerCmd = dockerClient
      .createContainerCmd(image.toString)
      .withAttachStdin(false)
      .withAttachStdout(true)
      .withAttachStderr(false)
      .withEnv(environment.asJava)
      .withCmd("/bin/bash", "-c", command.toString)

    name.map(config.withName)

    val portBindings                      = new Ports
    val exposedPorts: List[ExposedPort]   = for {
      (container, host) <- portMappings
    } yield {
      val exposed: ExposedPort = ExposedPort.tcp(container)
      portBindings.bind(exposed, Ports.Binding.bindPort(host))
      exposed
    }
    val links: Seq[Link]                  = dnsMappings.map { case (name, alias) =>
      new Link(name, alias)
    }
    config.getHostConfig.setLinks(links.toList*)
    val response: CreateContainerResponse = config
      .withExposedPorts(exposedPorts.asJava)
      .withHostConfig(config.getHostConfig.withPortBindings(portBindings))
      .exec
    response
  }

  private def start(
      dockerClient: DockerClient,
      container:    CreateContainerResponse,
  ): IO[ContainerId] = IO {
    dockerClient.startContainerCmd(container.getId).exec
    ContainerId(container.getId)
  }

}
