ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"             % "0.2.2")
addSbtPlugin("io.spray"                  % "sbt-revolver"             % "0.9.1")
addSbtPlugin("com.github.sbt"            % "sbt-native-packager"      % "1.9.9")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"             % "2.4.6")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix"             % "0.10.0")
addSbtPlugin("org.scala-js"              % "sbt-scalajs"              % "1.10.0")
addSbtPlugin("org.portable-scala"        % "sbt-scalajs-crossproject" % "1.2.0")
addSbtPlugin("org.scalameta"             % "sbt-mdoc"                 % "2.3.6")
addSbtPlugin("com.tapad"                 % "sbt-docker-compose"       % "1.0.35")
addSbtPlugin("se.marcuslonnberg"         % "sbt-docker"               % "1.9.0") // for building images
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")

