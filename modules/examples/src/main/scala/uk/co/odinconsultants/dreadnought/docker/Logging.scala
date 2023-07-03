package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{Deferred, IO}

object Logging {

  type LoggingLatch = (String, Deferred[IO, String]) => String => IO[Unit]

  def ioPrintln(colour: Option[String])(line: String): IO[Unit] =
    colour.map(x => IO.println(s"$x$line")).getOrElse(IO.println(line))

  def verboseWaitFor(
      colour: Option[String]
  )(seek:     String, deferred: Deferred[IO, String]): String => IO[Unit] = {
    val printer = ioPrintln(colour)
    (line: String) =>
      if (line.contains(seek)) printer(s"Started!\n$line") *> deferred.complete(line).void
      else printer(line)
  }

}
