This is an example of starting a small Spark cluster within Docker in just a few lines of code. 
```scala mdoc
import cats.effect.{IO, IOApp}
import uk.co.odinconsultants.dreadnought.Flow.race
import uk.co.odinconsultants.dreadnought.docker.Algebra.toInterpret
import uk.co.odinconsultants.dreadnought.docker.Logging.verboseWaitFor
import uk.co.odinconsultants.dreadnought.docker.SparkStructuredStreamingMain.startSparkCluster
import uk.co.odinconsultants.dreadnought.docker.ZKKafkaMain.startKafkaCluster
import uk.co.odinconsultants.dreadnought.docker.*
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration.*


def startCluster: IO[(ContainerId, ContainerId)] = for {
  client  <- CatsDocker.client
  ids     <- startSparkCluster(client, verboseWaitFor)
} yield ids

val (master, slave) = startCluster.unsafeRunSync()

```

Now, let's stop our containers:

```scala mdoc
def stopCluster: IO[Unit] = for {
    client <- CatsDocker.client
    _      <- race(toInterpret(client))(List(master, slave).map(StopRequest.apply))
} yield println(s"Stopped containers $master and $slave")

stopCluster.unsafeRunSync()
```
