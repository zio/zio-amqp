import sbt._

object Dependencies {

  val zioVersion = "2.1.26"
  lazy val deps  = Seq(
    "dev.zio"                %% "zio-streams"                   % zioVersion,
    "dev.zio"                %% "zio-interop-reactivestreams"   % "2.0.2",
    "com.rabbitmq"            % "amqp-client"                   % "5.37.0",
    "org.scala-lang.modules" %% "scala-collection-compat"       % "2.14.0",
    "dev.zio"                %% "zio-prelude"                   % "1.0.0-RC23",
    "com.dimafeng"           %% "testcontainers-scala-rabbitmq" % "0.44.1"   % Test,
    "dev.zio"                %% "zio-test"                      % zioVersion % Test,
    "dev.zio"                %% "zio-test-sbt"                  % zioVersion % Test,
    "ch.qos.logback"          % "logback-classic"               % "1.6.5"    % Test
  )
}
