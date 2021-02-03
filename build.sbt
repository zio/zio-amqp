val mainScala = "2.13.1"
val allScala  = Seq("2.11.12", "2.12.10", mainScala)

enablePlugins(GitVersioning)

inThisBuild(
  List(
    organization := "nl.vroste",
    homepage := Some(url("https://github.com/svroonland/zio-amqp")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    scalaVersion := mainScala,
    crossScalaVersions := allScala,
    parallelExecution in Test := false,
    fork in Test := true,
    fork in run := true,
    publishMavenStyle := true,
    publishArtifact in Test := false,
    assemblyJarName in assembly := "zio-amqp-" + version.value + ".jar",
    test in assembly := {},
    target in assembly := file(baseDirectory.value + "/../bin/"),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*)       => MergeStrategy.discard
      case n if n.startsWith("reference.conf") => MergeStrategy.concat
      case _                                   => MergeStrategy.first
    },
    bintrayOrganization := Some("vroste"),
    bintrayVcsUrl := Some("https://github.com/svroonland/zio-amqp"),
    bintrayReleaseOnPublish in ThisBuild := true,
    bintrayPackageLabels := Seq("zio", "amqp")
  )
)

name := "zio-amqp"
scalafmtOnCompile := true

libraryDependencies ++= Seq(
  "dev.zio"                %% "zio-streams"                 % "1.0.4-2",
  "dev.zio"                %% "zio-test"                    % "1.0.4-2" % "test",
  "dev.zio"                %% "zio-test-sbt"                % "1.0.4-2" % "test",
  "dev.zio"                %% "zio-interop-reactivestreams" % "1.3.0.7-2",
  "com.rabbitmq"            % "amqp-client"                 % "5.10.0",
  "ch.qos.logback"          % "logback-classic"             % "1.2.3",
  "org.scala-lang.modules" %% "scala-collection-compat"     % "2.4.1"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
