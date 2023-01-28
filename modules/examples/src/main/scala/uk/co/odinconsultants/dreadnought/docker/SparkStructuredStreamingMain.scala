package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{Deferred, IO, IOApp}
import cats.free.Free
import com.comcast.ip4s.*
import com.github.dockerjava.api.DockerClient
import fs2.kafka.*
import fs2.{Chunk, Pipe, Pure, Stream}
import uk.co.odinconsultants.dreadnought.docker.ZKKafkaMain.waitFor
import uk.co.odinconsultants.dreadnought.docker.*

import scala.concurrent.duration.*

object SparkStructuredStreamingMain extends IOApp.Simple {

  /** TODO
    * Pull images
    */
  def run: IO[Unit] = for {
    client     <- CatsDocker.client
    sparkStart <- Deferred[IO, String]
    spark      <- startMaster(sparkStart, port"8082", port"7077", client)
    _          <- sparkStart.get.timeout(10.seconds)
    masterName <- CatsDocker.interpret(client, Free.liftF(NamesRequest(spark)))
    slaveStart <- Deferred[IO, String]
    slave      <- startSlave(slaveStart, port"7077", masterName, client)
    _          <- slaveStart.get.timeout(10.seconds)
    _          <- CatsDocker.interpret(
                    client,
                    for {
                      _ <- Free.liftF(StopRequest(spark))
                      _ <- Free.liftF(StopRequest(slave))
                    } yield {},
                  )
  } yield println("Started and stopped" + spark)

  def startMaster(
      sparkStart:  Deferred[IO, String],
      webPort:     Port,
      servicePort: Port,
      client:      DockerClient,
  ): IO[ContainerId] = CatsDocker.interpret(client,
    for {
      spark <- Free.liftF(sparkMaster(webPort, servicePort))
      _     <-
        Free.liftF(
          LoggingRequest(spark, waitFor("I have been elected leader! New state: ALIVE", sparkStart))
        )
    } yield spark
  )

  def startSlave(
      sparkStart:  Deferred[IO, String],
      servicePort: Port,
      masterName:  List[String],
      client:      DockerClient,
  ): IO[ContainerId] = CatsDocker.interpret(client,
    for {
      spark <- Free.liftF(sparkSlave(masterName, servicePort))
      _     <-
        Free.liftF(
          LoggingRequest(spark, waitFor("Successfully registered with master", sparkStart))
        )
    } yield spark
  )

  def sparkMaster(webPort: Port, servicePort: Port): StartRequest = StartRequest(
    ImageName("bde2020/spark-master:3.2.1-hadoop3.2"),
    Command("/bin/bash /master.sh"),
    List("INIT_DAEMON_STEP=setup_spark"),
    List(8080 -> webPort.value, 7077 -> servicePort.value),
    List.empty,
  )

  def sparkSlave(masterName: List[String], servicePort: Port): StartRequest = StartRequest(
    ImageName("bde2020/spark-worker:3.2.1-hadoop3.2"),
    Command("/bin/bash /worker.sh"),
    List(s"SPARK_MASTER=spark://spark-master:${servicePort.value}"),
    List.empty,
    masterName.map(_ -> "spark-master"),
  )

}
