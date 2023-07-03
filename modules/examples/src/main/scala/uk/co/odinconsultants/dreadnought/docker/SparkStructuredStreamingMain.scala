package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{Deferred, IO, IOApp}
import cats.free.Free
import com.comcast.ip4s.*
import com.github.dockerjava.api.DockerClient
import fs2.kafka.*
import fs2.{Chunk, Pipe, Pure, Stream}
import uk.co.odinconsultants.dreadnought.Flow.{Interpret, race}
import uk.co.odinconsultants.dreadnought.docker.*
import uk.co.odinconsultants.dreadnought.docker.Algebra.toInterpret
import uk.co.odinconsultants.dreadnought.docker.Logging.{LoggingLatch, verboseWaitFor}

import scala.concurrent.duration.*

object SparkStructuredStreamingMain extends IOApp.Simple {

  /** TODO
    * Pull images
    */
  def run: IO[Unit] = for {
    client         <- CatsDocker.client
    (spark, slave) <- startSparkCluster(client, verboseWaitFor(None))
    _              <- race(toInterpret(client))(
                        List(spark, slave).map(StopRequest.apply)
                      )
  } yield println("Started and stopped" + spark)

  def startSparkCluster(
      client:       DockerClient,
      loggingLatch: LoggingLatch,
      timeout:      FiniteDuration = 10.seconds,
  ): IO[(ContainerId, ContainerId)] = for {
    spark      <- waitForMaster(client, loggingLatch, timeout)
    masterName <- CatsDocker.interpret(client, Free.liftF(NamesRequest(spark)))
    slave      <- waitForSlave(client, loggingLatch, timeout, masterName)
  } yield (spark, slave)

  def waitForSlave(
      client:       DockerClient,
      loggingLatch: LoggingLatch,
      timeout:      FiniteDuration,
      masterName:   List[String],
  ): IO[ContainerId] = for {
    slaveLatch <- Deferred[IO, String]
    slaveWait   = loggingLatch("Successfully registered with master", slaveLatch)
    slave      <- startSlave(port"7077", masterName, client, slaveWait)
    _          <- slaveLatch.get.timeout(timeout)
  } yield slave

  def waitForMaster(
      client:       DockerClient,
      loggingLatch: LoggingLatch,
      timeout:      FiniteDuration,
  ): IO[ContainerId] = for {
    sparkLatch <- Deferred[IO, String]
    sparkWait   = loggingLatch("I have been elected leader! New state: ALIVE", sparkLatch)
    spark      <- startMaster(port"8082", port"7077", client, sparkWait)
    _          <- sparkLatch.get.timeout(timeout)
  } yield spark

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
          LoggingRequest(spark, logging)
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
    ImageName("ph1ll1phenry/spark_master_3_3_0_scala_2_13_hadoop_3"),
    Command("/bin/bash /master.sh"),
    List("INIT_DAEMON_STEP=setup_spark"),
    List(8080 -> webPort.value, 7077 -> servicePort.value),
    List.empty,
  )

  def sparkSlave(masterName: List[String], servicePort: Port): StartRequest = StartRequest(
    ImageName("ph1ll1phenry/spark_worker_3_3_0_scala_2_13_hadoop_3"),
    Command("/bin/bash /worker.sh"),
    List(s"SPARK_MASTER=spark://spark-master:${servicePort.value}"),
    List.empty,
    masterName.map(_ -> "spark-master"),
  )

}
