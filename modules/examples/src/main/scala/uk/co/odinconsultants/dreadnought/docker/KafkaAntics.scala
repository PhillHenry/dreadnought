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
        .withGroupId("group")

//    consume(consumerSettings, topic).concurrently(produce(producerSettings, topic))
//    produce(producerSettings, topic).concurrently(consume(consumerSettings, topic))
//    produce(producerSettings, topic)
    consume(consumerSettings, topic).concurrently(fromFs2Kafka(producerSettings, topic))
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
      .emits(List("a", "b", "c", "d").zipWithIndex)
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

  def produce(
      producerSettings: ProducerSettings[IO, String, String],
      topic:            String,
  ) =
    KafkaProducer
      .stream(producerSettings)
      .flatMap { producer =>
        val s: Stream[IO, IO[ProducerResult[Int, String, String]]] = createPureMessages(topic)
          .evalMap { case record =>
            IO.println(s"buffering $record") *> producer.produce(record)
          }
        s // .evalMap(_.flatMap(IO.println))
      }
//      .evalTap(_.flatMap(IO.println)) // <- this causes: "org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for test_topic-0:120000 ms has passed since batch creation"
//    transactionalProducerStream(producerSettings, topic)

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
      .evalTap(x => IO.println(s"Created $x"))
      .flatMap { producer =>
        val txs = for {
          records <- createTxMessages(topic)
        } yield producer.produce(records)
        Stream.eval(IO.println("about to TX")) ++ txs.evalMap(_.flatMap(x => IO.println(x)))
//        producer.produceWithoutOffsets()
//          createPureMessages(topic).through(TransactionalKafkaProducer.pipe(producerSettings, producer))
      }
  def run: IO[Unit]                                                                       = produceMessages(ip"127.0.0.1", port"9092").compile.drain

}
