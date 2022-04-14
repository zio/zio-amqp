lazy val mainScala2_13 = "2.13.7"
lazy val scala2_12     = "2.12.15"
lazy val scala3        = "3.1.2"

lazy val `zio-amqp` = (project in file("."))
  .settings(name := "zio-amqp")
  .settings(Settings.org)
  .settings(
    scalaVersion       := mainScala2_13,
    crossScalaVersions := Seq(scala2_12, mainScala2_13, scala3)
  )
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
