package uk.co.odinconsultants.dreadnought.docker
import java.io.Closeable
import java.time.Duration
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.{CreateContainerCmd, CreateContainerResponse}
import com.github.dockerjava.api.model.{Container, Frame, Link}
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientImpl}
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient

/** All the imperative Docker API.
  */
object RawDocker {

  /** Smoke test
    *  * Delete everything with:
    * <pre>
    * docker stop $(docker ps -a -q) ; docker rm $(docker ps -a -q)
    * </pre>
    * See https://stackoverflow.com/questions/43135374/how-to-create-and-start-docker-container
    * * -with-specific-port-detached-mode-using
    */
  def main(args: Array[String]): Unit = {
    val config: DefaultDockerClientConfig = buildConfig("unix:///var/run/docker.sock", "1.41")
    val dockerClient: DockerClient        = DockerClientImpl.getInstance(config, buildClient(config))
    println(
      dockerClient.pingCmd().exec()
    ) // bizarrely, returns null if successful - see PingCmdExec.exec()
    import scala.jdk.CollectionConverters.*
    for {
      image <- dockerClient.listImagesCmd().exec().toArray()
    } yield println(s"Image: $image")

    dockerClient.close()
  }

  def stopContainer(dockerClient: DockerClient, container: Container): Unit = {
    val id: String = container.getId
    stopContainerWithId(dockerClient, id)
  }

  def stopContainerWithId(dockerClient: DockerClient, id: String): Unit =
    println(s"Stopping container with ID $id")
    dockerClient.stopContainerCmd(id).exec()

  def deleteContainerWithId(dockerClient: DockerClient, id: String): Unit =
    println(s"Stopping container with ID $id")
    dockerClient.removeContainerCmd(id).exec()

  def buildClient(config: DefaultDockerClientConfig): ApacheDockerHttpClient =
    new ApacheDockerHttpClient.Builder()
      .dockerHost(config.getDockerHost)
      .sslConfig(config.getSSLConfig)
      .maxConnections(100)
      .connectionTimeout(Duration.ofSeconds(30))
      .responseTimeout(Duration.ofSeconds(45))
      .build()

  def buildConfig(
      dockerHost: String,
      apiVersion: String,
  ): DefaultDockerClientConfig = DefaultDockerClientConfig
    .createDefaultConfigBuilder()
    .withDockerHost(dockerHost)
    .withApiVersion(apiVersion)
    .build()

  def listContainers(dockerClient: DockerClient): List[Container] =
    val containers: Array[Container] = for {
      container <- dockerClient
                     .listContainersCmd()
                     .exec()
                     .toArray()
                     .map(_.asInstanceOf[Container])
    } yield {
      println(
        s"id = ${container.getId}, image = ${container.getImage}, names = ${container.getNames
            .mkString(", ")}"
      )
      container
    }
    containers.toList

  def reporter[T](report: String => T): ResultCallback[Frame] = new ResultCallback[Frame] {
    override def onError(throwable: Throwable): Unit = report(throwable.toString)
    override def onNext(x: Frame): Unit              = report(s"onNext: $x")
    override def onStart(closeable: Closeable): Unit = report(s"onStart: closeable = $closeable")
    override def onComplete(): Unit                  = report("Complete")
    override def close(): Unit                       = report("close")
  }

  def logStdOut(dockerClient: DockerClient, id: String): ResultCallback[Frame] = {
    def report(msg: String): String = s"$id $msg"
    log(dockerClient, id, report)
  }

  def log[T](dockerClient: DockerClient, id: String, report: String => T): ResultCallback[Frame] =
    dockerClient
      .logContainerCmd(id)
      .withStdOut(true)
      .withTailAll().withSince(-1).withStdErr(true)
      .withFollowStream(true)
      .exec(reporter(report))

}
