package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{Deferred, IO}

object Logging {
  
  type LoggingLatch = (String, Deferred[IO, String]) => String => IO[Unit]

  def verboseWaitFor(colour: Option[String])(seek: String,
                     deferred: Deferred[IO, String]): String => IO[Unit] = {
    def ioPrintln(line: String, colour: Option[String]): IO[Unit]
      = colour.map(x => IO.println(s"$x$line")).getOrElse(IO.println(line))

    (line: String) =>
      if (line.contains(seek)) ioPrintln(s"Started!\n$line", colour) *> deferred.complete(line).void
      else ioPrintln(line, colour)
  }
      
}
