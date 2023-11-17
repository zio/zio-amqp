import zio.sbt.githubactions.Step.SingleStep

inThisBuild(
  List(
    name               := "ZIO AMQP",
    crossScalaVersions := Seq(scalaVersion.value),
    developers         := Settings.devs,
    ciEnabledBranches  := Seq("master"),
    ciTestJobs         := ciTestJobs.value.map(x =>
      x.copy(steps =
        (x.steps.reverse.head +:
          SingleStep(
            name = "Start containers",
            run = Some("docker-compose -f docker-compose.yml up -d --build")
          ) +: x.steps.reverse.tail).reverse
      )
    )
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    headerEndYear  := Some(2023),
    publish / skip := true
  )
  .aggregate(
    `zio-amqp`,
    docs
  )
  .enablePlugins(ZioSbtCiPlugin, ZioSbtEcosystemPlugin)

lazy val `zio-amqp` = (project in file("zio-amqp"))
  .settings(name := "zio-amqp")
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("zio.amqp"))
  .settings(stdSettings(Some("zio-amqp")))
  .settings(scala3Settings)
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

lazy val docs = project
  .in(file("zio-amqp-docs"))
  .settings(
    moduleName                                 := "zio-amqp-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := (ThisBuild / name).value,
    mainModuleName                             := (`zio-amqp` / moduleName).value,
    projectStage                               := ProjectStage.Development,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(`zio-amqp`),
    headerLicense                              := None
  )
  .dependsOn(`zio-amqp`)
  .enablePlugins(WebsitePlugin)
