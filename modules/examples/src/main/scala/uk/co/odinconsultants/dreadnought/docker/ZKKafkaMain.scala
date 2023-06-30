package uk.co.odinconsultants.dreadnought.docker
import cats.effect.{Deferred, IO, IOApp}
import cats.free.Free
import com.comcast.ip4s.Port
import uk.co.odinconsultants.dreadnought.docker.CatsDocker.{client, interpret}
import com.comcast.ip4s.*
import com.github.dockerjava.api.DockerClient
import uk.co.odinconsultants.dreadnought.docker.Logging.{LoggingLatch, verboseWaitFor}
import uk.co.odinconsultants.dreadnought.docker.PopularContainers.{startKafkaOnPort, startZookeeper}

import scala.concurrent.duration.*

object ZKKafkaMain extends IOApp.Simple {
  def run: IO[Unit] =
    for {
      client      <- client
      (zk, kafka) <- startKafkaCluster(client, verboseWaitFor)
      _           <- interpret(client, tearDownFree(zk, kafka))
    } yield println("Started and stopped")

  def startKafkaCluster(
      client:       DockerClient,
      loggingLatch: LoggingLatch,
      timeout:      FiniteDuration = 10.seconds,
  ): IO[(ContainerId, ContainerId)] = for {
    kafkaStart  <- Deferred[IO, String]
    zkStart     <- Deferred[IO, String]
    kafkaLatch   = loggingLatch("started (kafka.server.Kafka", kafkaStart)
    zkLatch      = loggingLatch("Started AdminServer on address", zkStart)
    (zk, kafka) <- interpret(client, kafkaEcosystem(kafkaLatch, zkLatch))
    _           <- kafkaStart.get.timeout(timeout)
    _           <- zkStart.get.timeout(timeout)
  } yield (zk, kafka)

  def kafkaEcosystem(
      kafkaLogging: String => IO[Unit],
      zkLogging:    String => IO[Unit],
  ): Free[ManagerRequest, (ContainerId, ContainerId)] =
    for {
      zookeeper <- Free.liftF(startZookeeper(port"2182"))
      names     <- Free.liftF(NamesRequest(zookeeper))
      kafka1    <- Free.liftF(startKafkaOnPort(port"9092", names))
      _         <- Free.liftF(
                     LoggingRequest(kafka1, kafkaLogging)
                   )
      _         <- Free.liftF(
                     LoggingRequest(zookeeper, zkLogging)
                   )
    } yield (zookeeper, kafka1)

  def tearDownFree(zookeeper: ContainerId, kafka1: ContainerId): Free[ManagerRequest, Unit] =
    for {
      _ <- Free.liftF(StopRequest(zookeeper))
      _ <- Free.liftF(StopRequest(kafka1))
    } yield {}

}
