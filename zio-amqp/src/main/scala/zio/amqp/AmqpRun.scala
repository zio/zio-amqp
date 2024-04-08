package zio.amqp

import zio._
import zio.amqp.model.{ AMQPConfig, ConsumerTag, ExchangeName, ExchangeType, QueueName, RoutingKey }
import zio.stream.ZStream

import java.nio.charset.StandardCharsets

object AmqpRun extends ZIOAppDefault {

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    ZIO.logLevel(LogLevel.Debug) {
      val app = for {
        _               <- ZStream.log("Starting")
        connection      <- ZStream.fromZIO(Amqp.connect(AMQPConfig.default))
        confirmschannel <- ZStream.fromZIO(Amqp.createChannelConfirmsDefault(connection))
        queue            = QueueName("test-queue")
        exchange         = ExchangeName("test")
        _               <- ZStream.fromZIO(confirmschannel.queueDeclare(queue))
        _               <- ZStream.fromZIO(confirmschannel.exchangeDeclare(exchange, ExchangeType.Direct))
        _               <- ZStream.fromZIO(confirmschannel.queueBind(queue, exchange, RoutingKey("")))
        publisher        = ZStream.tick(3.seconds).mapZIO { _ =>
                             zio.Random
                               .nextString(10)
                               .flatMap(content => confirmschannel.publish(exchange, content.getBytes()))
                           }
        q                = confirmschannel.getQueue
        confirmLogic     =
          ZStream
            .fromQueue(q)
            .tap(entry =>
              ZIO.logInfo(s"Received ${entry.deliveryTag} ${new String(entry.content, StandardCharsets.UTF_8)}")
            )
        _               <- confirmschannel
                             .consume(queue, ConsumerTag("consumer1"), autoAck = true)
                             .drainFork(publisher)
                             .drainFork(confirmLogic)
      } yield ()
      app.retry(Schedule.spaced(5.seconds) ++ Schedule.recurs(20)).runDrain
    }
}
