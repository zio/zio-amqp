import sbt.Keys.{ developers, homepage, licenses, organization, scmInfo }
import sbt.{ url, Developer, ScmInfo }
import BuildHelper._

object Settings {
  lazy val devs = List(
    Developer(
      "Adriani277",
      "Adriani Furtado",
      "adrini.furtado@gmail.com",
      url("https://github.com/Adriani277")
    )
  )

  lazy val org = Seq(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev/ecosystem/community/zio-amqp/")),
    licenses     := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers   := devs,
    scmInfo      := Some(
      ScmInfo(
        url("https://github.com/zio/zio-amqp"),
        "scm:git:git@github.com:zio/zio-amqp.git"
      )
    )
  )
}
