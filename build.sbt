import BuildHelper._

lazy val `zio-amqp` = (project in file("zio-amqp"))
  .settings(name := "zio-amqp")
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("zio.amqp"))
  .settings(stdSettings("zio-amqp"))
  .settings(dottySettings)
  .settings(Settings.org)
  .settings(
    libraryDependencies ++= Dependencies.deps
  )
  .settings(
    Test / parallelExecution := false,
    Test / fork              := true,
    Test / publishArtifact   := false,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .settings(
    run / fork := true
  )
  .settings(
    assembly / assemblyJarName       := "zio-amqp-" + version.value + ".jar",
    assembly / test                  := {},
    assembly / target                := file(baseDirectory.value + "/../bin/"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*)       => MergeStrategy.discard
      case n if n.startsWith("reference.conf") => MergeStrategy.concat
      case _                                   => MergeStrategy.first
    }
  )
  .settings(
    scalafmtOnCompile := true
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
