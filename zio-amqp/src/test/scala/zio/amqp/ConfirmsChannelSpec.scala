package zio.amqp

import com.rabbitmq.client.ConnectionFactory
import zio._
import zio.amqp.RabbitContainerDetails.containerDetails
import zio.amqp.model._
import zio.stream.ZStream
import zio.test._

import java.util.UUID

object ConfirmsChanneSpec extends ZIOSpecDefault {

  def spec = suite("ConfirmsChannelSpec")(
    test("publish delivers messages with publish confirms") {
      val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
      val exchangeName   = ExchangeName(s"exchange-$testAmqpSuffix")
      val queueName      = QueueName(s"queue-$testAmqpSuffix")
      val numMessages    = 20000
      val messages       = (1 to numMessages).map(i => s"$i " + UUID.randomUUID.toString)
      val factory        = new ConnectionFactory()

      ZIO.service[ContainerDetails].flatMap { container =>
        factory.setHost(container.host)
        factory.setPort(container.amqpPort)

        Amqp
          .connect(factory)
          .tap(_ => ZIO.log("Connected!"))
          .flatMap(Amqp.createChannelConfirmsDefault)
          .tap(_ => ZIO.log("Created channel!"))
          .flatMap { channel =>
            for {
              _             <- channel.queueDeclare(queueName)
              _             <-
                channel.exchangeDeclare(
                  exchangeName,
                  ExchangeType.Custom("fanout")
                ) // doesn't actually need to be a custom exchange type, just testing to make sure custom exchange types "work"
              _             <- channel.queueBind(queueName, exchangeName, RoutingKey("myroutingkey"))
              queue          = channel.getQueue
              confirmsFiber <- ZStream.fromQueue(queue).take(2000).runCollect.fork
              _             <-
                ZIO.foreachParDiscard(0 until numMessages)(i => channel.publish(exchangeName, messages(i).getBytes))
              bodies        <- channel
                                 .consume(queue = queueName, consumerTag = ConsumerTag("test"))
                                 .take(numMessages.toLong)
                                 .runCollect
                                 .tap { records =>
                                   val tag = records.last.getEnvelope.getDeliveryTag
                                   channel.ack(DeliveryTag(tag))
                                 }
                                 .map(_.map(r => new String(r.getBody)))
              confirms      <- confirmsFiber.join
              _             <- channel.queueDelete(queueName)
              _             <- channel.exchangeDelete(exchangeName)
            } yield assertTrue(messages.toSet == bodies.toSet, confirms.size == 2000)
          }
      }
    } @@ TestAspect.timeout(2.minutes) @@ TestAspect.flaky
  ).provideSome[Scope](containerDetails)
}
