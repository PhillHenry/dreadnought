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
  ) = {
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
      .interruptAfter(100.seconds)
      .concurrently(produce(producerSettings, topic))
  }

  def consume(
      consumerSettings: ConsumerSettings[IO, String, String],
      topic:            String,
  ): Stream[IO, Unit] =
    KafkaConsumer
      .stream(consumerSettings)
      .subscribeTo(topic)
      .records
      .evalMap { committable =>
        val record = committable.record
        IO.println(s"Consumed ${record.key} -> ${record.value}")
      }

  def createPureMessages(topic: String): Stream[IO, ProducerRecords[Int, String, String]] =
    Stream
      .emits(List("a", "b", "c", "d").zipWithIndex)
      .evalTap(x => IO.println(s"Creating message $x"))
      .map((x, i) => ProducerRecords.one(ProducerRecord(topic, s"key_$x", s"val_$x"), i))
      .covary[IO]

  def createTxMessages(
      topic: String
  ): Stream[IO, TransactionalProducerRecords[IO, Unit, String, String]] =
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
              _ => IO.unit,
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
          producerSettings.withRetries(1),
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
    _           <- produceMessages(ip"127.0.0.1", port"9092").compile.drain
    _           <- CatsDocker.interpret(client, ZKKafkaMain.tearDownFree(zk, kafka))
  } yield println("Started and stopped")

}
