This is an example of starting a small Spark cluster within Docker in just a few lines of code. 
```scala mdoc
import cats.implicits._
import cats.effect.IO
import scala.concurrent.duration._
import cats.effect.unsafe.implicits.global

import cats.effect.{IO, IOApp}
import uk.co.odinconsultants.dreadnought.Flow.race
import uk.co.odinconsultants.dreadnought.docker.Algebra.toInterpret
import uk.co.odinconsultants.dreadnought.docker.Logging.verboseWaitFor
import uk.co.odinconsultants.dreadnought.docker.SparkStructuredStreamingMain.startSparkCluster
import uk.co.odinconsultants.dreadnought.docker.ZKKafkaMain.startKafkaCluster
import uk.co.odinconsultants.dreadnought.docker.*

import scala.concurrent.duration.*

def run: IO[Unit] = for {
  client         <- CatsDocker.client

  (master, slave) <- startSparkCluster(client, verboseWaitFor)
} yield println("Started and stopped")

run.unsafeRunSync()

```