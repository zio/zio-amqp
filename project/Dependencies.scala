import sbt._

object Dependencies {

  val zioVersion = "2.0.2"
  lazy val deps  = Seq(
    "dev.zio"                %% "zio-streams"                 % zioVersion,
    "dev.zio"                %% "zio-test"                    % zioVersion % Test,
    "dev.zio"                %% "zio-test-sbt"                % zioVersion % Test,
    "dev.zio"                %% "zio-interop-reactivestreams" % "2.0.0",
    "com.rabbitmq"            % "amqp-client"                 % "5.18.0",
    "ch.qos.logback"          % "logback-classic"             % "1.2.11"   % Test,
    "org.scala-lang.modules" %% "scala-collection-compat"     % "2.11.0",
    "dev.zio"                %% "zio-prelude"                 % "1.0.0-RC15"
  )
}
