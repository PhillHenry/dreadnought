This is an example of starting a small Kafka cluster within Docker in just a few lines of code. 
```scala mdoc
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

val (zk, kafka) = startCluster.unsafeRunSync()

```
Let's send some messages:

```scala mdoc
import fs2.Stream
import com.comcast.ip4s.*
import uk.co.odinconsultants.dreadnought.docker.KafkaAntics.produceMessages

val address                = ip"127.0.0.1"
val zkPort                 = port"9092"
val topicName              = "test_topic"
val sendMessages: IO[Unit] = for {
    _ <- produceMessages(address, zkPort, topicName)
             .handleErrorWith(x => Stream.eval(IO(x.printStackTrace())))
             .compile
             .drain
} yield println("Sent messages")

sendMessages.unsafeRunSync()
```

Now, let's consume those messages:

```scala mdoc
import uk.co.odinconsultants.dreadnought.docker.KafkaAntics.consume
import fs2.kafka.{ConsumerSettings, ProducerRecords, ProducerSettings, *}
import scala.concurrent.duration.*

val consumerSettings =
  ConsumerSettings[IO, String, String]
    .withAutoOffsetReset(AutoOffsetReset.Earliest)
    .withBootstrapServers(s"${address}:${zkPort.value}")
    .withGroupId("my_group")
    
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

consumerStream.unsafeRunSync()
```


Now, let's stop our containers:

```scala mdoc
def stopCluster: IO[Unit] = for {
    client <- CatsDocker.client
    _      <- race(toInterpret(client))(List(zk, kafka).map(StopRequest.apply))
} yield println(s"Stopped containers $zk and $kafka")

stopCluster.unsafeRunSync()
```
