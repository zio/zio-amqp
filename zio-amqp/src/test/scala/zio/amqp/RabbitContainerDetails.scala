package zio.amqp

import com.dimafeng.testcontainers.RabbitMQContainer
import zio._

object RabbitContainerDetails {
  val containerDetails: ZLayer[Scope, Throwable, ContainerDetails] =
    ZLayer.fromZIO(
      zio.System.env("CI").flatMap {
        case Some(_) =>
          ZIO.succeed(ContainerDetails("localhost", 5672)).debug("Running on CI, using dedicated RabbitMQ")
        case None    =>
          ZIO
            .acquireRelease(ZIO.attempt(RabbitMQContainer.Def().start()))(c => ZIO.attempt(c.stop()).orDie)
            .map(c => ContainerDetails(c.host, c.amqpPort))
            .debug("Running locally, using testcontainers RabbitMQ")

      }
    )
}
