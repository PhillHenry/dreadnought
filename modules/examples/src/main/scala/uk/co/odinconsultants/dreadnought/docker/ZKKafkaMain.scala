package uk.co.odinconsultants.dreadnought.docker
import cats.effect.{Deferred, IO, IOApp}
import cats.free.Free
import com.comcast.ip4s.Port
import uk.co.odinconsultants.dreadnought.docker.CatsDocker.{client, interpret}
import com.comcast.ip4s.*
import com.github.dockerjava.api.DockerClient

object ZKKafkaMain extends IOApp.Simple {
  def run: IO[Unit] =
    for {
      client <- client
      _      <- waitForStack(client)
    } yield println("Started and stopped")

  val startZookeeper: StartRequest = StartRequest(
    ImageName("docker.io/bitnami/zookeeper:3.8"),
    Command("/entrypoint.sh /opt/bitnami/scripts/zookeeper/run.sh"),
    List("ALLOW_ANONYMOUS_LOGIN=yes"),
    List(2181 -> 2182),
    List.empty,
  )

  def waitFor(seek: String, deferred: Deferred[IO, String]): String => IO[Unit] =
    (line: String) =>
      if (line.contains(seek)) IO.println(s"Started!\n$line") *> deferred.complete(line).void
      else IO.println(line)

  def waitForStack(client: DockerClient): IO[Unit] = for {
    kafkaStart    <- Deferred[IO, String]
    zkStart       <- Deferred[IO, String]
    (zk, kafka)   <- interpret(client, buildFree(kafkaStart, zkStart))
    kafkaStartMsg <- kafkaStart.get
    zkStartMsg    <- zkStart.get
    _             <- interpret(client, tearDownFree(zk, kafka))
  } yield println(s"Kafka start message = $kafkaStartMsg\nZK start message = $zkStartMsg")

  def buildFree(
      kafkaStart: Deferred[IO, String],
      zkStart: Deferred[IO, String],
  ): Free[ManagerRequest, (ContainerId, ContainerId)] =
    for {
      zookeeper <- Free.liftF(startZookeeper)
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

  private def startKafkaOnPort(
      hostPort: Port,
      names: List[String],
  ) = StartRequest(
    ImageName("bitnami/kafka:latest"),
    Command("/opt/bitnami/scripts/kafka/entrypoint.sh /run.sh"),
    List("KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181", "ALLOW_PLAINTEXT_LISTENER=yes"),
    List(9092 -> hostPort.value),
    names.map(_ -> "zookeeper"),
  )
}
