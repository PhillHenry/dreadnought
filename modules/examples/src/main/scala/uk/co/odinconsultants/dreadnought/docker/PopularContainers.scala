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

  /** See
    * https://stackoverflow.com/questions/48731030/no-security-protocol-defined-for-listener-plaintext-tcp
    * https://www.confluent.io/en-gb/blog/kafka-listeners-explained/
    * https://stackoverflow.com/questions/46750420/kafka-producer-error-expiring-10-records-for-topicxxxxxx-6686-ms-has-passed
    * https://stackoverflow.com/questions/42998859/kafka-server-configuration-listeners-vs-advertised-listeners
    * https://stackoverflow.com/questions/30880811/kafka-quickstart-advertised-host-name-gives-kafka-common-leadernotavailableexce
    */
  def startKafkaOnPort(
      hostPort: Port,
      names:    List[String],
  ): StartRequest = StartRequest(
    ImageName("bitnami/kafka:latest"),
    Command("/opt/bitnami/scripts/kafka/entrypoint.sh /run.sh"),
    List(
      "KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181",
      "ALLOW_PLAINTEXT_LISTENER=yes",
//      "KAFKA_INTER_BROKER_LISTENER_NAME=LISTENER_BOB",
//      "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=LISTENER_BOB:PLAINTEXT",
      s"KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:${hostPort.value}",
      "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1",
      "KAFKA_TRANSACTION_ABORT_TIMED_OUT_TRANSACTION_CLEANUP_INTERVAL_MS=60000",
      "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1",
      "KAFKA_AUTHORIZER_CLASS_NAME=kafka.security.authorizer.AclAuthorizer",
      "KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND=true",
    ),
    List(9092 -> hostPort.value),
    names.map(_ -> "zookeeper"),
  )
}
