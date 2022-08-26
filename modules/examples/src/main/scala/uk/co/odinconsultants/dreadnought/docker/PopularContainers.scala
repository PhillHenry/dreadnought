package uk.co.odinconsultants.dreadnought.docker
import com.comcast.ip4s.Port

object PopularContainers {

  def startZookeeper(hostPort: Port): StartRequest = StartRequest(
    ImageName("docker.io/bitnami/zookeeper:3.8"),
    Command("/entrypoint.sh /opt/bitnami/scripts/zookeeper/run.sh"),
    List("ALLOW_ANONYMOUS_LOGIN=yes"),
    List(2181 -> hostPort.value),
    List.empty,
  )

  def startKafkaOnPort(
      hostPort: Port,
      names:    List[String],
  ): StartRequest = StartRequest(
    ImageName("bitnami/kafka:latest"),
    Command("/opt/bitnami/scripts/kafka/entrypoint.sh /run.sh"),
    List("KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181", "ALLOW_PLAINTEXT_LISTENER=yes"),
    List(9092 -> hostPort.value),
    names.map(_ -> "zookeeper"),
  )
}
