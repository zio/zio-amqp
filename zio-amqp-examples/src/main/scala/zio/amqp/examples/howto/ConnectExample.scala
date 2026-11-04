//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.0
// Run from this folder:  scala-cli run ConnectExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.ConnectExample"

package zio.amqp.examples.howto

import com.rabbitmq.client.ConnectionFactory
import zio._
import zio.amqp._
import zio.amqp.model._

import java.net.URI

/**
 * Connect to a Broker
 *
 * Opens a connection from an AMQPConfig, from a URI and from a ConnectionFactory.
 */
object ConnectExample extends ZIOAppDefault {

  val fromConfig: ZIO[Scope, Throwable, com.rabbitmq.client.Connection] =
    Amqp.connect(AMQPConfig.default.copy(host = "localhost", port = 5672))

  val fromUri: ZIO[Scope, Throwable, com.rabbitmq.client.Connection] =
    Amqp.connect(URI.create("amqp://guest:guest@localhost:5672/%2F"))

  val fromFactory: ZIO[Scope, Throwable, com.rabbitmq.client.Connection] = {
    val factory = new ConnectionFactory()
    factory.setHost("localhost")
    factory.setPort(5672)
    factory.setUsername("guest")
    factory.setPassword("guest")
    Amqp.connect(factory)
  }

  def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        a <- fromConfig
        b <- fromUri
        c <- fromFactory
        _ <- Console.printLine(s"config open: ${a.isOpen}, uri open: ${b.isOpen}, factory open: ${c.isOpen}")
      } yield ()
    }
}
