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
//       thunk = uk.co.odinconsultants.dreadnought.docker.CatsDocker$$$Lambda$29264/0x000000010277d440@65156c66,
//       event = cats.effect.tracing.TracingEvent$StackTrace
//     ),
//     f = uk.co.odinconsultants.dreadnought.docker.CatsDocker$$$Lambda$29266/0x000000010277b840@5f924e8e,
//     event = cats.effect.tracing.TracingEvent$StackTrace
//   ),
//   f = repl.MdocSession$MdocApp$$Lambda$29267/0x000000010277a840@50e042a5,
//   event = cats.effect.tracing.TracingEvent$StackTrace
// )

val (zk, kafka) = startCluster.unsafeRunSync()
// id = 56930c0ba46cc386c49a318c9989636617bdc7e21dc75b1cc28dd4c3284c0877, image = docker.io/bitnami/zookeeper:3.8, names = /vibrant_clarke
// zk: ContainerId = "56930c0ba46cc386c49a318c9989636617bdc7e21dc75b1cc28dd4c3284c0877"
// kafka: ContainerId = "0e22a78905e87488efc005bb199db0523a1a8d397e9eed67b4011e4b188118a2"
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
//     body = cats.effect.IO$$$Lambda$29374/0x0000000101c09040@6d90d958,
//     event = cats.effect.tracing.TracingEvent$StackTrace
//   ),
//   f = repl.MdocSession$MdocApp$$Lambda$30176/0x00000001033d3840@3e84c867,
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
//   keyDeserializer = Pure(value = Deserializer$860634043),
//   valueDeserializer = Pure(value = Deserializer$1240328375),
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
//   recordMetadata = fs2.kafka.ConsumerSettings$$$Lambda$30151/0x000000010341c840@71180bb0,
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
//     f = fs2.Stream$CompileOps$$Lambda$30788/0x0000000104c53040@5544c313,
//     event = cats.effect.tracing.TracingEvent$StackTrace
//   ),
//   f = repl.MdocSession$MdocApp$$Lambda$30789/0x0000000104c52840@4445877f,
//   event = cats.effect.tracing.TracingEvent$StackTrace
// )

consumerStream.unsafeRunSync()
// res1: Vector[Matchable] = Vector(
//   CommittableConsumerRecord(ConsumerRecord(topic = test_topic, partition = 0, offset = 0, key = key_z, value = val_2, timestamp = Timestamp(createTime = 1676107624651), serializedKeySize = 5, serializedValueSize = 5, leaderEpoch = 0), CommittableOffset(test_topic-0 -> 1, my_group)),
//   CommittableConsumerRecord(ConsumerRecord(topic = test_topic, partition = 1, offset = 0, key = key_x, value = val_0, timestamp = Timestamp(createTime = 1676107624318), serializedKeySize = 5, serializedValueSize = 5, leaderEpoch = 0), CommittableOffset(test_topic-1 -> 1, my_group)),
//   CommittableConsumerRecord(ConsumerRecord(topic = test_topic, partition = 1, offset = 2, key = key_y, value = val_1, timestamp = Timestamp(createTime = 1676107624502), serializedKeySize = 5, serializedValueSize = 5, leaderEpoch = 0), CommittableOffset(test_topic-1 -> 3, my_group))
// )
```


Now, let's stop our containers:

```scala
def stopCluster: IO[Unit] = for {
    client <- CatsDocker.client
    _      <- race(toInterpret(client))(List(zk, kafka).map(StopRequest.apply))
} yield println(s"Stopped containers $zk and $kafka")

stopCluster.unsafeRunSync()
// Stopping container with ID 56930c0ba46cc386c49a318c9989636617bdc7e21dc75b1cc28dd4c3284c0877
// Stopping container with ID 0e22a78905e87488efc005bb199db0523a1a8d397e9eed67b4011e4b188118a2
// Stopped containers 56930c0ba46cc386c49a318c9989636617bdc7e21dc75b1cc28dd4c3284c0877 and 0e22a78905e87488efc005bb199db0523a1a8d397e9eed67b4011e4b188118a2
```
