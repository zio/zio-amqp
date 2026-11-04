//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.0
// Run from this folder:  scala-cli run CompleteExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.tutorial.CompleteExample"

package zio.amqp.examples.tutorial

import com.rabbitmq.client.{ Connection, MessageProperties }
import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Work Queue Tutorial — Putting It Together
 *
 * Publishes ten tasks and processes them with two competing workers, each on its own channel with a prefetch of one.
 */
object CompleteExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("tutorial-tasks")
  val taskCount: Int   = 10

  def worker(
    connection: Connection,
    name: String,
    processed: Ref[Int],
    finished: Promise[Nothing, Unit]
  ): ZIO[Scope, Throwable, Unit] =
    for {
      channel <- Amqp.createChannel(connection)
      _       <- channel.basicQos(1)
      _       <- channel
                   .consume(queue, ConsumerTag(name))
                   .mapZIO { delivery =>
                     val task = new String(delivery.getBody, "UTF-8")
                     for {
                       _ <- Console.printLine(s"[$name] working on $task")
                       _ <- ZIO.sleep(200.millis)
                       _ <- channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
                       n <- processed.updateAndGet(_ + 1)
                       _ <- finished.succeed(()).when(n == taskCount)
                     } yield ()
                   }
                   .runDrain
                   .forkScoped
    } yield ()

  val program: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel.queueDeclare(queue, durable = true)
        _          <- channel.purgeQueue(queue)
        processed  <- Ref.make(0)
        finished   <- Promise.make[Nothing, Unit]
        _          <- worker(connection, "worker-1", processed, finished)
        _          <- worker(connection, "worker-2", processed, finished)
        _          <- ZIO.foreachDiscard(1 to taskCount) { i =>
                        channel.publish(
                          exchange = ExchangeName(""),
                          body = s"task-$i".getBytes("UTF-8"),
                          routingKey = RoutingKey(QueueName.unwrap(queue)),
                          props = MessageProperties.PERSISTENT_TEXT_PLAIN
                        )
                      }
        _          <- finished.await
        left       <- channel.messageCount(queue)
        _          <- Console.printLine(s"All $taskCount tasks done, $left messages left in the queue")
      } yield ()
    }

  def run: ZIO[Any, Throwable, Unit] = program
}
