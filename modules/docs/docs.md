This is an example of starting a small Spark cluster within Docker in just a few lines of code. 
```scala mdoc
import cats.implicits._
import cats.effect.IO
import scala.concurrent.duration._
import cats.effect.unsafe.implicits.global
import uk.co.odinconsultants.dreadnought.docker.SparkStructuredStreamingMain.startSparkCluster
```