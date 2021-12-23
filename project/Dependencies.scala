import sbt._

object Dependencies {

  val zioVersion = "2.0.0-RC1"
  lazy val deps  = Seq(
    "dev.zio"                %% "zio-streams"                 % zioVersion,
    "dev.zio"                %% "zio-test"                    % zioVersion % Test,
    "dev.zio"                %% "zio-test-sbt"                % zioVersion % Test,
    "dev.zio"                %% "zio-interop-reactivestreams" % "1.3.8",
    "com.rabbitmq"            % "amqp-client"                 % "5.14.0",
    "ch.qos.logback"          % "logback-classic"             % "1.2.10"   % Test,
    "org.scala-lang.modules" %% "scala-collection-compat"     % "2.6.0",
    "dev.zio"                %% "zio-prelude"                 % "1.0.0-RC9"
  )
}
