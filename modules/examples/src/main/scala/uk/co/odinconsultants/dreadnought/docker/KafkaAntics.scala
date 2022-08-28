package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import fs2.kafka.{ConsumerSettings, ProducerRecords, ProducerSettings, *}
import fs2.{Pipe, Pure, Stream}

import scala.concurrent.duration.*

object KafkaAntics extends IOApp.Simple {

  def produceMessages(
      address: IpAddress,
      port:    Port,
      topic:   String = "test_topic",
  ) = {
    val bootstrapServer                                        = s"localhost:${port.value}"
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
    produce(producerSettings, topic)
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

//  def createTxMessages(topic: String) =
//    createPureMessages(topic)
//      .map(records => TransactionalProducerRecords.one(CommittableProducerRecords(records, ???)))
//      .covary[IO]

  def produce(
      producerSettings: ProducerSettings[IO, String, String],
      topic:            String,
  ) =
    KafkaProducer
      .stream(producerSettings)
      .flatMap { producer =>
        val s: Stream[IO, IO[ProducerResult[Int, String, String]]] = createPureMessages(topic)
          .evalMap { case producerRecord =>
            val product: IO[IO[ProducerResult[Int, String, String]]] =
              producer.produce(producerRecord)
            IO.println(s"buffering $producerRecord") *> product
          }
        s // .evalMap(_.flatMap(IO.println))
      }
//      .groupWithin(500, 5.seconds)
//      .evalMap(x => IO.println(s"Sending $x") *> x.flatMap(x => IO.println(s"Result = $x")))
//      .evalMap(x => IO.println(s"Sent $x"))
//    val pipe: Pipe[IO, ProducerRecords[Int, String, String], ProducerResult[Int, String, String]] =
//      KafkaProducer.pipe(producerSettings)
//    val piped: Stream[IO, ProducerResult[Int, String, String]]                                    = pipe(createPureMessages(topic))
//    pStream
//  }
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
        val txs: Stream[IO, IO[ProducerResult[Int, String, String]]] = for {
          records <- createPureMessages(topic)
        } yield producer.produceWithoutOffsets(records)
        Stream.eval(IO.println("about to TX")) ++ txs.evalMap(_.flatMap(x => IO.println(x)))
//        producer.produceWithoutOffsets()
//          createPureMessages(topic).through(TransactionalKafkaProducer.pipe(producerSettings, producer))
      }
  def run: IO[Unit] = produceMessages(ip"127.0.0.1", port"9092").compile.drain

}
