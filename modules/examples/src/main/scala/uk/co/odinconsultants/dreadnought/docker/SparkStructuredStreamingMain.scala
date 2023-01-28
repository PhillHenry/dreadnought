package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{Deferred, IO, IOApp}
import cats.free.Free
import com.comcast.ip4s.*
import com.github.dockerjava.api.DockerClient
import fs2.kafka.*
import fs2.{Chunk, Pipe, Pure, Stream}
import uk.co.odinconsultants.dreadnought.Flow.Interpret
import uk.co.odinconsultants.dreadnought.docker.*
import uk.co.odinconsultants.dreadnought.docker.Logging.{verboseWaitFor, LoggingLatch}

import scala.concurrent.duration.*

object SparkStructuredStreamingMain extends IOApp.Simple {

  /** TODO
    * Pull images
    */
  def run: IO[Unit] = for {
    client         <- CatsDocker.client
    (spark, slave) <- startSparkCluster(client, verboseWaitFor)
    _              <- CatsDocker.interpret(
                        client,
                        for {
                          _ <- Free.liftF(StopRequest(spark))
                          _ <- Free.liftF(StopRequest(slave))
                        } yield {},
                      )
  } yield println("Started and stopped" + spark)

  def startSparkCluster(
      client:       DockerClient,
      loggingLatch: LoggingLatch,
      timeout:      FiniteDuration = 10.seconds,
  ): IO[(ContainerId, ContainerId)] = for {
    sparkStart <- Deferred[IO, String]
    sparkWait   = loggingLatch("I have been elected leader! New state: ALIVE", sparkStart)
    spark      <- startMaster(port"8082", port"7077", client, sparkWait)
    _          <- sparkStart.get.timeout(timeout)
    masterName <- CatsDocker.interpret(client, Free.liftF(NamesRequest(spark)))
    slaveStart <- Deferred[IO, String]
    slaveWait   = loggingLatch("Successfully registered with master", slaveStart)
    slave      <- startSlave(port"7077", masterName, client, slaveWait)
    _          <- slaveStart.get.timeout(timeout)
  } yield (spark, slave)

  def startMaster(
      webPort:     Port,
      servicePort: Port,
      client:      DockerClient,
      logging:     String => IO[Unit],
  ): IO[ContainerId] = CatsDocker.interpret(
    client,
    for {
      spark <- Free.liftF(sparkMaster(webPort, servicePort))
      _     <-
        Free.liftF(
          LoggingRequest(
            spark,
            logging,
          )
        )
    } yield spark,
  )

  def startSlave(
      servicePort: Port,
      masterName:  List[String],
      client:      DockerClient,
      logging:     String => IO[Unit],
  ): IO[ContainerId] = CatsDocker.interpret(
    client,
    for {
      spark <- Free.liftF(sparkSlave(masterName, servicePort))
      _     <-
        Free.liftF(
          LoggingRequest(spark, logging)
        )
    } yield spark,
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
