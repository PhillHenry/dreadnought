package uk.co.odinconsultants.dreadnought.docker
import cats.effect.{Deferred, IO, IOApp}
import cats.free.Free
import com.comcast.ip4s.Port
import uk.co.odinconsultants.dreadnought.docker.CatsDocker.{client, interpret}
import com.comcast.ip4s.*
import com.github.dockerjava.api.DockerClient
import uk.co.odinconsultants.dreadnought.docker.PopularContainers.{startKafkaOnPort, startZookeeper}

object ZKKafkaMain extends IOApp.Simple {
  def run: IO[Unit] =
    for {
      client      <- client
      (zk, kafka) <- waitForStack(client)
      _           <- interpret(client, tearDownFree(zk, kafka))
    } yield println("Started and stopped")

  def waitFor(seek: String, deferred: Deferred[IO, String]): String => IO[Unit] =
    (line: String) =>
      if (line.contains(seek)) IO.println(s"Started!\n$line") *> deferred.complete(line).void
      else IO.println(line)

  def waitForStack(client: DockerClient): IO[(ContainerId, ContainerId)] = for {
    kafkaStart    <- Deferred[IO, String]
    zkStart       <- Deferred[IO, String]
    (zk, kafka)   <- interpret(client, buildFree(kafkaStart, zkStart))
    kafkaStartMsg <- kafkaStart.get
    zkStartMsg    <- zkStart.get
  } yield (zk, kafka)

  def buildFree(
      kafkaStart: Deferred[IO, String],
      zkStart:    Deferred[IO, String],
  ): Free[ManagerRequest, (ContainerId, ContainerId)] =
    for {
      zookeeper <- Free.liftF(startZookeeper(port"2182"))
      names     <- Free.liftF(NamesRequest(zookeeper))
      kafka1    <- Free.liftF(startKafkaOnPort(port"9092", names))
      _         <- Free.liftF(
                     LoggingRequest(kafka1, waitFor("started (kafka.server.KafkaServer)", kafkaStart))
                   )
      _         <- Free.liftF(LoggingRequest(zookeeper, waitFor("Started AdminServer on address", zkStart)))
    } yield (zookeeper, kafka1)

  def tearDownFree(zookeeper: ContainerId, kafka1: ContainerId): Free[ManagerRequest, Unit] =
    for {
      _ <- Free.liftF(StopRequest(zookeeper))
      _ <- Free.liftF(StopRequest(kafka1))
    } yield {}

}
