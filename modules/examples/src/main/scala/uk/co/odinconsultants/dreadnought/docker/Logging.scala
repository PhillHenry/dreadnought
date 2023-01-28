package uk.co.odinconsultants.dreadnought.docker

import cats.effect.{Deferred, IO}

object Logging {
  
  type LoggingLatch = (String, Deferred[IO, String]) => String => IO[Unit]

  def verboseWaitFor(seek: String, deferred: Deferred[IO, String]): String => IO[Unit] =
    (line: String) =>
      if (line.contains(seek)) IO.println(s"Started!\n$line") *> deferred.complete(line).void
      else IO.println(line)
      
}
