package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import fs2.kafka.ProducerSettings
import fs2.kafka.*
import fs2.Stream

import scala.concurrent.duration.*

object KafkaAntics extends IOApp.Simple {

  def produceMessages(
      address: IpAddress,
      port:    Port,
      topic:   String = "test_topic",
  ): Stream[IO, Int] = {
    val producerSettings: ProducerSettings[IO, String, String] =
      ProducerSettings[IO, String, String]
        .withBootstrapServers(s"$address:${port.value}")

    produce(producerSettings, topic)
  }

  def produce(
      producerSettings: ProducerSettings[IO, String, String],
      topic:            String,
  ): Stream[IO, Int] =
    KafkaProducer.stream(producerSettings).flatMap { producer =>
      Stream
        .emits(List("a", "b", "c").zipWithIndex)
        .map((x, i) => i -> ProducerRecords.one(ProducerRecord(topic, s"key_$x", s"val_$x")))
        .evalMap { case (offset, producerRecord) =>
          producer
            .produce(producerRecord)
            .map(_.as(offset))
        }
        .parEvalMap(Int.MaxValue)(identity)
    }

  def run: IO[Unit] = produceMessages(ip"127.0.0.1", port"9092").compile.drain

}
