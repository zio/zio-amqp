import sbt._

object Dependencies {

  val zioVersion = "2.0.13"
  lazy val deps  = Seq(
    "dev.zio"                %% "zio"                         % zioVersion,
    "dev.zio"                %% "zio-streams"                 % zioVersion,
    "dev.zio"                %% "zio-interop-reactivestreams" % "2.0.1",
    "com.rabbitmq"            % "amqp-client"                 % "5.16.0",
    "org.scala-lang.modules" %% "scala-collection-compat"     % "2.8.1",
    "dev.zio"                %% "zio-prelude"                 % "1.0.0-RC18",
    "dev.zio"                %% "zio-test"                    % zioVersion % Test,
    "dev.zio"                %% "zio-test-sbt"                % zioVersion % Test,
    "ch.qos.logback"          % "logback-classic"             % "1.4.6"    % Test
  )
}
