# Introduction

Dreadnought orchestrates the use of containers in a few lines of code.

This example uses Scala/Cats.

In this example, we're going to:
1. start Zookeeper and Kafka Docker containers
2. send messages to the Kafka instance
3. read those messages back 
4. then close down the whole cluster 

all within a single JVM.

# Code

This is an example of starting a small Kafka cluster within Docker in just a few lines of code. 
```scala
import cats.effect.{IO, IOApp}
import uk.co.odinconsultants.dreadnought.Flow.race
import uk.co.odinconsultants.dreadnought.docker.Algebra.toInterpret
import uk.co.odinconsultants.dreadnought.docker.Logging.verboseWaitFor
import uk.co.odinconsultants.dreadnought.docker.ZKKafkaMain.startKafkaCluster
import uk.co.odinconsultants.dreadnought.docker.*
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration.*


val startCluster: IO[(ContainerId, ContainerId)] = for {
  client      <- CatsDocker.client
  (zk, kafka) <- ZKKafkaMain.startKafkaCluster(client, verboseWaitFor)
} yield (zk, kafka)
// startCluster: IO[Tuple2[ContainerId, ContainerId]] = FlatMap(
//   ioe = HandleErrorWith(
//     ioa = Delay(
//       thunk = uk.co.odinconsultants.dreadnought.docker.CatsDocker$$$Lambda$33223/0x00000001049ad440@75e70ff3,
//       event = cats.effect.tracing.TracingEvent$StackTrace
//     ),
//     f = uk.co.odinconsultants.dreadnought.docker.CatsDocker$$$Lambda$33225/0x00000001049ab840@5ebc262f,
//     event = cats.effect.tracing.TracingEvent$StackTrace
//   ),
//   f = repl.MdocSession$MdocApp$$Lambda$33226/0x00000001049aa840@41bda5ea,
//   event = cats.effect.tracing.TracingEvent$StackTrace
// )

val (zk, kafka) = startCluster.unsafeRunSync()
// id = 11f973a03a6db0de7e3c5696ae4d5482cd99d87f3de2f19e4ca2e4a06f1c4b5e, image = docker.io/bitnami/zookeeper:3.8, names = /modest_beaver
// zk: ContainerId = "11f973a03a6db0de7e3c5696ae4d5482cd99d87f3de2f19e4ca2e4a06f1c4b5e"
// kafka: ContainerId = "eed1804a3a33d446a7202ba406a04a679292bebfa4d0223ae830650bd4344eab"
```
Let's send some messages:

```scala
import fs2.Stream
import com.comcast.ip4s.*
import uk.co.odinconsultants.dreadnought.docker.KafkaAntics.produceMessages

val address                = ip"127.0.0.1"
// address: IpAddress = 127.0.0.1
val zkPort                 = port"9092"
// zkPort: Port = ( = 9092)
val topicName              = "test_topic"
// topicName: String = "test_topic"
val sendMessages: IO[Unit] = for {
    _ <- produceMessages(address, zkPort, topicName)
             .handleErrorWith(x => Stream.eval(IO(x.printStackTrace())))
             .compile
             .drain
} yield println("Sent messages")
// Creating test_topic
// Admin client result: null
// sendMessages: IO[Unit] = Map(
//   ioe = Uncancelable(
//     body = cats.effect.IO$$$Lambda$33333/0x0000000104691040@28227765,
//     event = cats.effect.tracing.TracingEvent$StackTrace
//   ),
//   f = repl.MdocSession$MdocApp$$Lambda$34097/0x0000000104a4b040@2ffd0e43,
//   event = cats.effect.tracing.TracingEvent$StackTrace
// )

sendMessages.unsafeRunSync()
// Sent messages
```

Now, let's consume those messages:

```scala
import uk.co.odinconsultants.dreadnought.docker.KafkaAntics.consume
import fs2.kafka.{ConsumerSettings, ProducerRecords, ProducerSettings, *}
import scala.concurrent.duration.*

val consumerSettings =
  ConsumerSettings[IO, String, String]
    .withAutoOffsetReset(AutoOffsetReset.Earliest)
    .withBootstrapServers(s"${address}:${zkPort.value}")
    .withGroupId("my_group")
// consumerSettings: ConsumerSettings[[A >: Nothing <: Any] => IO[A], String, String] = ConsumerSettingsImpl(
//   keyDeserializer = Pure(value = Deserializer$548780889),
//   valueDeserializer = Pure(value = Deserializer$211337975),
//   customBlockingContext = None,
//   properties = Map(
//     "auto.offset.reset" -> "earliest",
//     "enable.auto.commit" -> "false",
//     "bootstrap.servers" -> "127.0.0.1:9092",
//     "group.id" -> "my_group"
//   ),
//   closeTimeout = 20 seconds,
//   commitTimeout = 15 seconds,
//   pollInterval = 50 milliseconds,
//   pollTimeout = 50 milliseconds,
//   commitRecovery = Default,
//   recordMetadata = fs2.kafka.ConsumerSettings$$$Lambda$34072/0x0000000104a2c040@53ab0fa3,
//   maxPrefetchBatches = 2
// )
    
val consumerStream = for {
  messages <- KafkaConsumer.stream(consumerSettings)
                .subscribeTo(topicName)
                .records
                .evalMap { (committable: CommittableConsumerRecord[IO, String, String]) =>
                  val record = committable.record
                  IO.println(s"Consumed ${record.key} -> ${record.value}") *> IO(committable)
                }
                .handleErrorWith(x => Stream.eval(IO(x.printStackTrace())))
                .interruptAfter(10.seconds)
                .compile
                .toVector
} yield { messages }
// consumerStream: IO[Vector[Matchable]] = Map(
//   ioe = FlatMap(
//     ioe = Pure(value = ()),
//     f = fs2.Stream$CompileOps$$Lambda$34708/0x0000000105551840@c41a14e,
//     event = cats.effect.tracing.TracingEvent$StackTrace
//   ),
//   f = repl.MdocSession$MdocApp$$Lambda$34709/0x0000000105552840@1c8b2369,
//   event = cats.effect.tracing.TracingEvent$StackTrace
// )

consumerStream.unsafeRunSync()
// res1: Vector[Matchable] = Vector(
//   CommittableConsumerRecord(ConsumerRecord(topic = test_topic, partition = 1, offset = 0, key = key_x, value = val_0, timestamp = Timestamp(createTime = 1676108514112), serializedKeySize = 5, serializedValueSize = 5, leaderEpoch = 0), CommittableOffset(test_topic-1 -> 1, my_group)),
//   CommittableConsumerRecord(ConsumerRecord(topic = test_topic, partition = 1, offset = 2, key = key_y, value = val_1, timestamp = Timestamp(createTime = 1676108514402), serializedKeySize = 5, serializedValueSize = 5, leaderEpoch = 0), CommittableOffset(test_topic-1 -> 3, my_group)),
//   CommittableConsumerRecord(ConsumerRecord(topic = test_topic, partition = 0, offset = 0, key = key_z, value = val_2, timestamp = Timestamp(createTime = 1676108514538), serializedKeySize = 5, serializedValueSize = 5, leaderEpoch = 0), CommittableOffset(test_topic-0 -> 1, my_group))
// )
```


Now, let's stop our containers:

```scala
def stopCluster: IO[Unit] = for {
    client <- CatsDocker.client
    _      <- race(toInterpret(client))(List(zk, kafka).map(StopRequest.apply))
} yield println(s"Stopped containers $zk and $kafka")

stopCluster.unsafeRunSync()
// Stopping container with ID 11f973a03a6db0de7e3c5696ae4d5482cd99d87f3de2f19e4ca2e4a06f1c4b5e
// Stopping container with ID eed1804a3a33d446a7202ba406a04a679292bebfa4d0223ae830650bd4344eab
// Stopped containers 11f973a03a6db0de7e3c5696ae4d5482cd99d87f3de2f19e4ca2e4a06f1c4b5e and eed1804a3a33d446a7202ba406a04a679292bebfa4d0223ae830650bd4344eab
```
