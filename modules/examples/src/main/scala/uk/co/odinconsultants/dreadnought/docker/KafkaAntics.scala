package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import fs2.kafka.{ConsumerSettings, ProducerRecords, ProducerSettings, *}
import fs2.{Chunk, Pipe, Pure, Stream}

import scala.concurrent.duration.*

object KafkaAntics extends IOApp.Simple {

  def produceMessages(
      address: IpAddress,
      port:    Port,
      topic:   String = "test_topic",
  ): Stream[IO, CommittableConsumerRecord[IO, String, String]] = {
    createCustomTopic(topic)

    val bootstrapServer                                        = s"${address}:${port.value}"
    val producerSettings: ProducerSettings[IO, String, String] =
      ProducerSettings[IO, String, String]
        .withBootstrapServers(bootstrapServer)

    val consumerSettings =
      ConsumerSettings[IO, String, String]
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withBootstrapServers(bootstrapServer)
        .withGroupId("group_PH")

    consume(consumerSettings, topic)
      .interruptAfter(10.seconds)
      .concurrently(
        produce(producerSettings, topic).map(_ => ())
      )
  }

  def consume(
      consumerSettings: ConsumerSettings[IO, String, String],
      topic:            String,
  ): Stream[IO, CommittableConsumerRecord[IO, String, String]] =
    KafkaConsumer
      .stream(consumerSettings)
      .subscribeTo(topic)
      .records
      .evalMap { (committable: CommittableConsumerRecord[IO, String, String]) =>
        val record = committable.record
        IO.println(s"Consumed ${record.key} -> ${record.value}") *> IO(committable)
      }

  def createPureMessages(topic: String): Stream[IO, ProducerRecords[String, String]] =
    Stream
      .emits(List("a", "b", "c", "d").zipWithIndex)
      .evalTap(x => IO.println(s"Creating message $x"))
      .map((x, i) => ProducerRecords.one(ProducerRecord(topic, s"key_$x", s"val_$x")))
      .covary[IO]
  import org.apache.kafka.clients.admin.NewTopic
  import scala.jdk.CollectionConverters.*
  import scala.util.Try
  import org.apache.kafka.clients.admin.AdminClient
  import java.util.concurrent.TimeUnit
  import org.apache.kafka.clients.admin.AdminClientConfig

  /** "I made the replication factor less than the number of partitions and it worked for me.
    * It sounds odd to me but yes, it started working after it."
    * https://stackoverflow.com/questions/61217084/error-while-fetching-metadata-with-correlation-id-92-mytest-unknown-topic-or
    */
  def createCustomTopic(
      topic:             String,
      topicConfig:       Map[String, String] = Map.empty,
      partitions:        Int = 2,
      replicationFactor: Int = 1,
  ): Try[Unit] = {
    println(s"Creating $topic")
    val newTopic = new NewTopic(topic, partitions, replicationFactor.toShort)
      .configs(topicConfig.asJava)

    withAdminClient { adminClient =>
      adminClient
        .createTopics(Seq(newTopic).asJava)
        .all
        .get(4, TimeUnit.SECONDS)
    }.map(x => println(s"Admin client result: $x"))
  }
  protected def withAdminClient[T](
      body: AdminClient => T
  ): Try[T] = {
    val adminClientCloseTimeout: FiniteDuration = 2.seconds
    val adminClient                             = AdminClient.create(
      Map[String, Object](
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG       -> "127.0.0.1:9092",
        AdminClientConfig.CLIENT_ID_CONFIG               -> "test-kafka-admin-client",
        AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG      -> "10000",
        AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG -> "10000",
      ).asJava
    )

    val res = Try(body(adminClient))
    adminClient.close(java.time.Duration.ofMillis(adminClientCloseTimeout.toMillis))

    res
  }

  def createTxMessages(
      topic: String
  ): Stream[IO, TransactionalProducerRecords[IO, String, String]] =
    Stream
      .emits(List("x", "y", "z").zipWithIndex)
      .map { case (k, v) =>
        TransactionalProducerRecords.one(
          CommittableProducerRecords.one(
            ProducerRecord(topic, s"key_$k", s"val_$v"),
            CommittableOffset[IO](
              new org.apache.kafka.common.TopicPartition(topic, 1),
              new org.apache.kafka.clients.consumer.OffsetAndMetadata(1),
              Some("group"),
              x => IO.println(s"offset/partition = $x"),
            ),
          )
        )
      }
      .covary[IO]
  def createCommittableMessages(
      topic: String
  ): Stream[IO, CommittableProducerRecords[IO, String, String]] =
    Stream
      .emits(List("x", "y", "z").zipWithIndex)
      .map { case (k, v) =>
        CommittableProducerRecords.one(
          ProducerRecord(topic, s"key_$k", s"val_$v"),
          CommittableOffset[IO](
            new org.apache.kafka.common.TopicPartition(topic, 1),
            new org.apache.kafka.clients.consumer.OffsetAndMetadata(1),
            Some("group"),
            _ => IO.unit,
          ),
        )
      }
      .covary[IO]

  def produce(
      producerSettings: ProducerSettings[IO, String, String],
      topic:            String,
  ) =
//    KafkaProducer
//      .stream(producerSettings)
//      .flatMap { producer =>
//        val s: Stream[IO, IO[ProducerResult[Int, String, String]]] = createPureMessages(topic)
//          .evalMap { case record =>
//            IO.println(s"buffering $record") *> producer.produce(record)
//          }
//        s // .evalMap(_.flatMap(IO.println))
//      }
//      .evalTap(_.flatMap(IO.println)) // <- this causes: "org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:120000 ms has passed since batch creation"
    transactionalProducerStream(producerSettings, topic)

  def fromFs2Kafka(producerSettings: ProducerSettings[IO, String, String], topic: String) = {
//    createCustomTopic(topic)
    val toProduce = (0 until 10).map(n => s"key-$n" -> s"value->$n")
    val sProduced =
      for {
        producer               <- KafkaProducer.stream(producerSettings)
        (records, passthrough) <-
          Stream.chunk(Chunk.seq(toProduce).map { case passthrough @ (key, value) =>
            (ProducerRecords.one(ProducerRecord(topic, key, value)), passthrough)
          })
        batched                <- Stream
                                    .eval(producer.produce(records))
                                    .map(_.as(passthrough))
                                    .buffer(toProduce.size)
        passthrough            <- Stream.eval(batched)
      } yield passthrough
    sProduced
  }
//      .through(commitBatchWithin(500, 15.seconds))

  /** createTxMessage and producer.produce( gives:
    * buffering TransactionalProducerRecords(CommittableProducerRecords(ProducerRecord(topic =
    *  test_topic, key = key_x, value = val_0), CommittableOffset(test_topic-1 -> 1, group)), ())
    * org.apache.kafka.common.errors.TimeoutException: Timeout expired after 60000 milliseconds while
    * awaiting EndTxn(false)
    * org.apache.kafka.common.errors.TimeoutException: Timeout expired after 60000 milliseconds while
    * awaiting AddOffsetsToTxn
    * org.apache.kafka.common.errors.TimeoutException: Timeout expired after 60000 milliseconds while
    * awaiting AddOffsetsToTxn
    */
  private def transactionalProducerStream(
      producerSettings: ProducerSettings[IO, String, String],
      topic:            String,
  ) =
    TransactionalKafkaProducer
      .stream(
        TransactionalProducerSettings(
          s"transactionId${System.currentTimeMillis()}",
          producerSettings.withRetries(10),
        )
      )
      .flatMap { producer =>
        val txs = produceTransactionally(producer, topic)
        Stream.eval(IO.println("about to TX")) ++ txs
//        producer.produceWithoutOffsets()
      }

  private def produceTransactionally(
      producer: TransactionalKafkaProducer[IO, String, String],
      topic:    String,
  ) =
    createTxMessages(topic)
      .evalMap { case record =>
        IO.println(s"buffering $record") *> producer.produce(record)
      }

  /** This works
    */
  private def produceWithoutOffsets(
      producer: TransactionalKafkaProducer.WithoutOffsets[IO, String, String],
      topic:    String,
  ) =
    createPureMessages(topic).evalMap { case record =>
      IO.println(s"buffering $record") *> producer.produceWithoutOffsets(record)
    }

  def run: IO[Unit] = for {
    client      <- CatsDocker.client
    (zk, kafka) <- ZKKafkaMain.waitForStack(client)
    _           <- produceMessages(ip"127.0.0.1", port"9092")
                     .handleErrorWith(x => Stream.eval(IO(x.printStackTrace())))
                     .compile
                     .drain
    _           <- CatsDocker.interpret(client, ZKKafkaMain.tearDownFree(zk, kafka))
  } yield println("Started and stopped")

}
