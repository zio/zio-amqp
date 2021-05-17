val mainScala = "2.13.6"
val allScala  = Seq("2.11.12", "2.12.12", mainScala)

inThisBuild(
  List(
    organization := "nl.vroste",
    homepage := Some(url("https://github.com/svroonland/zio-amqp")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "svroonland",
        "Vroste",
        "info@vroste.nl",
        url("https://github.com/svroonland")
      )
    ),
    scmInfo := Some(
      ScmInfo(url("https://github.com/svroonland/zio-amqp/"), "scm:git:git@github.com:svroonland/zio-amqp.git")
    ),
    scalaVersion := mainScala,
    crossScalaVersions := allScala,
    parallelExecution in Test := false,
    fork in Test := true,
    fork in run := true,
    publishArtifact in Test := false,
    assemblyJarName in assembly := "zio-amqp-" + version.value + ".jar",
    test in assembly := {},
    target in assembly := file(baseDirectory.value + "/../bin/"),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*)       => MergeStrategy.discard
      case n if n.startsWith("reference.conf") => MergeStrategy.concat
      case _                                   => MergeStrategy.first
    }
  )
)

name := "zio-amqp"
scalafmtOnCompile := true

libraryDependencies ++= Seq(
  "dev.zio"                %% "zio-streams"                 % "1.0.7",
  "dev.zio"                %% "zio-test"                    % "1.0.7" % Test,
  "dev.zio"                %% "zio-test-sbt"                % "1.0.7" % Test,
  "dev.zio"                %% "zio-interop-reactivestreams" % "1.3.4",
  "com.rabbitmq"            % "amqp-client"                 % "5.12.0",
  "ch.qos.logback"          % "logback-classic"             % "1.2.3" % Test,
  "org.scala-lang.modules" %% "scala-collection-compat"     % "2.4.4"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
