import sbt._

object Dependencies {

  val zioVersion = "2.0.21"
  lazy val deps  = Seq(
    "dev.zio"                %% "zio-streams"                   % zioVersion,
    "dev.zio"                %% "zio-interop-reactivestreams"   % "2.0.2",
    "com.rabbitmq"            % "amqp-client"                   % "5.20.0",
    "org.scala-lang.modules" %% "scala-collection-compat"       % "2.11.0",
    "dev.zio"                %% "zio-prelude"                   % "1.0.0-RC21",
    "com.dimafeng"           %% "testcontainers-scala-rabbitmq" % "0.41.0"   % Test,
    "dev.zio"                %% "zio-test"                      % zioVersion % Test,
    "dev.zio"                %% "zio-test-sbt"                  % zioVersion % Test,
    "ch.qos.logback"          % "logback-classic"               % "1.4.14"   % Test,
    "org.scalameta"           % "semanticdb-scalac_2.13.12"     % "4.8.14"
  )
}
