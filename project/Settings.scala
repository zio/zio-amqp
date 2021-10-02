import sbt.Keys.{ developers, homepage, licenses, organization, scmInfo }
import sbt.{ url, Developer, ScmInfo }

object Settings {
  lazy val devs = List(
    Developer(
      "svroonland",
      "Vroste",
      "info@vroste.nl",
      url("https://github.com/svroonland")
    )
  )

  lazy val org = Seq(
    organization := "nl.vroste",
    homepage     := Some(url("https://github.com/svroonland/zio-amqp")),
    licenses     := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers   := devs,
    scmInfo      := Some(
      ScmInfo(
        url("https://github.com/svroonland/zio-amqp/"),
        "scm:git:git@github.com:svroonland/zio-amqp.git"
      )
    )
  )
}
