package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import fs2.kafka.{ConsumerSettings, ProducerSettings, *}
import fs2.Stream

import scala.concurrent.duration.*

object KafkaAntics extends IOApp.Simple {

  def produceMessages(
      address: IpAddress,
      port:    Port,
      topic:   String = "test_topic",
  ): Stream[IO, Unit] = {
    val bootstrapServer                                        = s"localhost:${port.value}"
    val producerSettings: ProducerSettings[IO, String, String] =
      ProducerSettings[IO, String, String]
        .withBootstrapServers(bootstrapServer)

    val consumerSettings =
      ConsumerSettings[IO, String, String]
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withBootstrapServers(bootstrapServer)
        .withGroupId("group")

    consume(consumerSettings, topic).concurrently(produce(producerSettings, topic))
//    produce(producerSettings, topic).concurrently(consume(consumerSettings, topic))
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

  def produce(
      producerSettings: ProducerSettings[IO, String, String],
      topic:            String,
  ) =
    KafkaProducer
      .stream(producerSettings)
      .flatMap { producer =>
        val s: Stream[IO, IO[ProducerResult[Unit, String, String]]] = Stream
          .emits(List("a", "b", "c", "d").zipWithIndex)
          .map((x, i) => i -> ProducerRecords.one(ProducerRecord(topic, s"key_$x", s"val_$x")))
          .evalMap { case (offset, producerRecord) =>
            val product: IO[IO[ProducerResult[Unit, String, String]]] =
              producer.produce(producerRecord)
            IO.println(s"buffering $producerRecord, offset = $offset") *> product
          }
        s // .evalMap(_.flatMap(IO.println))
      }
//      .groupWithin(500, 5.seconds)
      .evalMap(_.flatMap(x => IO.println(s"Sending $x")))
//      .through(commitBatchWithin(500, 15.seconds))

  def run: IO[Unit] = produceMessages(ip"127.0.0.1", port"9092").compile.drain

}
