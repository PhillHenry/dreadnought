package uk.co.odinconsultants.dreadnought
import cats.*
import cats.data.*
import cats.effect.{Deferred, IO, IOApp}
import cats.free.Free
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.kafka.*
import fs2.{Chunk, Pipe, Pure, Stream}
import uk.co.odinconsultants.dreadnought.docker.*

import scala.concurrent.duration.*

object Flow {

  type Interpret[A] = Free[ManagerRequest, A] => IO[A]

  def process(
      interpreter: Interpret[ContainerId]
  )(xs:       NonEmptyList[ManagerRequest[ContainerId]]): IO[ContainerId] = {
    val value: Free[ManagerRequest, ContainerId] =
      xs.map(Free.liftF).reduce { case (x, y) => x >> y }
    interpreter(value)
  }

  def race(interpreter: Interpret[Unit])(xs: List[ManagerRequest[Unit]]): IO[Unit] =
    xs.map(Free.liftF).map(interpreter(_).void).parSequence_
    
}
