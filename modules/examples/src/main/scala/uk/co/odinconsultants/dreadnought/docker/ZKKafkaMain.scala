package uk.co.odinconsultants.dreadnought.docker
import cats.effect.{IO, IOApp}
import cats.free.Free
import uk.co.odinconsultants.dreadnought.docker.CatsDocker.{client, interpret}

object ZKKafkaMain extends IOApp.Simple {
  def run: IO[Unit] =
    for {
      client <- client
      _      <- interpret(client, buildFree)
      //      _      <- IO.println("Press any key to exit") *> IO(scala.io.StdIn.readLine())
    } yield println("Started and stopped")

  val startZookeeper: StartRequest = StartRequest(
    ImageName("docker.io/bitnami/zookeeper:3.8"),
    Command("/entrypoint.sh /opt/bitnami/scripts/zookeeper/run.sh"),
    List("ALLOW_ANONYMOUS_LOGIN=yes"),
    List(2181 -> 2182),
    List.empty,
  )

  def buildFree: Free[ManagerRequest, Unit] = for {
    zookeeper <- Free.liftF(startZookeeper)
    names     <- Free.liftF(NamesRequest(zookeeper))
    kafka     <- Free.liftF(
                   StartRequest(
                     ImageName("bitnami/kafka:latest"),
                     Command("/opt/bitnami/scripts/kafka/entrypoint.sh /run.sh"),
                     List("KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181", "ALLOW_PLAINTEXT_LISTENER=yes"),
                     List(9092 -> 9093),
                     names.map(_ -> "zookeeper"),
                   )
                 )
    _         <- Free.liftF(StopRequest(zookeeper))
    _         <- Free.liftF(StopRequest(kafka))
  } yield {}
}
