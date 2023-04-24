package nl.vroste.zio.amqp

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.dimafeng.testcontainers.GenericContainer
import com.rabbitmq.client.ConnectionFactory
import nl.vroste.zio.amqp.model._
import org.testcontainers.containers.wait.strategy.Wait
import zio.test.TestAspect.{ timeout, withLiveClock }
import zio.test._
import zio.testcontainers.StartableOps
import zio.{ durationInt, Console, Duration, Scope, ZIO }

object AmqpClientSpec extends ZIOSpecDefault {
  private class RabbitmqContainer
      extends GenericContainer(
        dockerImage = "rabbitmq:3.11",
        exposedPorts = Seq(5672),
        waitStrategy = Some(Wait.forListeningPort())
      ) {
    lazy val uri: URI =
      URI.create(
        s"amqp://guest:guest@${this.container.getHost}:${this.container.getFirstMappedPort}"
      )
  }

  private def amqpChannel: ZIO[Scope with RabbitmqContainer, Throwable, Channel] =
    for {
      container  <- ZIO.service[RabbitmqContainer]
      _          <- ZIO.logInfo(s"RabbitMQ URI:  ${container.uri}")
      factory     = {
        val factory = new ConnectionFactory()
        factory.setUri(container.uri)
        factory
      }
      connection <- Amqp.connect(factory)
      channel    <- Amqp.createChannel(connection)
    } yield channel

  override def spec =
    suite("AmqpClientSpec")(
      test("Amqp.consume delivers messages") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val exchangeName   = ExchangeName(s"exchange-$testAmqpSuffix")
        val queueName      = QueueName(s"queue-$testAmqpSuffix")
        val message1       = UUID.randomUUID().toString
        val message2       = UUID.randomUUID().toString
        val messages       = Set(message1, message2)

        ZIO.scoped {
          for {
            channel <- amqpChannel
            _       <- channel.queueDeclare(queueName)
            _       <- channel.exchangeDeclare(exchangeName, ExchangeType.Fanout)
            _       <- channel.queueBind(queueName, exchangeName, RoutingKey("myroutingkey"))
            _       <- channel.publish(exchangeName, message1.getBytes)
            _       <- channel.publish(exchangeName, message2.getBytes)
            bodies  <- channel
                         .consume(queue = queueName, consumerTag = ConsumerTag("test"))
                         .mapZIO { record =>
                           println(s"${record.getEnvelope.getDeliveryTag}: ${new String(record.getBody)}")
                           ZIO.succeed(record)
                         }
                         .take(2)
                         .runCollect
                         .tap { records =>
                           val tag = records.last.getEnvelope.getDeliveryTag
                           println(s"At tag: $tag")
                           channel.ack(DeliveryTag(tag))
                         }
                         .map(_.map(r => new String(r.getBody)))
            _       <- channel.queueDelete(queueName)
            _       <- channel.exchangeDelete(exchangeName)
          } yield assertTrue(messages == bodies.toSet)
        }
      } @@ timeout(Duration(10, TimeUnit.SECONDS)),
      test("Amqp.publish delivers messages with high concurrency") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val exchangeName   = ExchangeName(s"exchange-$testAmqpSuffix")
        val queueName      = QueueName(s"queue-$testAmqpSuffix")
        val numMessages    = 10000
        val messages       = (1 to numMessages).map(i => s"$i " + UUID.randomUUID.toString)

        ZIO.scoped {
          for {
            channel <- amqpChannel
            _       <- channel.queueDeclare(queueName)
            _       <-
              channel.exchangeDeclare(
                exchangeName,
                ExchangeType.Custom("fanout")
              ) // doesn't actually need to be a custom exchange type, just testing to make sure custom exchange types "work"
            _       <- channel.queueBind(queueName, exchangeName, RoutingKey("myroutingkey"))
            _       <- ZIO.foreachParDiscard(0 until numMessages)(i => channel.publish(exchangeName, messages(i).getBytes))
            bodies  <- channel
                         .consume(queue = queueName, consumerTag = ConsumerTag("test"))
                         .mapZIO(record =>
                           Console.printLine(s"consuming record ${new String(record.getBody)}")
                             *> ZIO.succeed(record)
                         )
                         .take(numMessages.toLong)
                         .runCollect
                         .tap { records =>
                           val tag = records.last.getEnvelope.getDeliveryTag
                           channel.ack(DeliveryTag(tag))
                         }
                         .map(_.map(r => new String(r.getBody)))
            _       <- channel.queueDelete(queueName)
            _       <- channel.exchangeDelete(exchangeName)
          } yield assertTrue(messages.toSet == bodies.toSet)
        }
      } @@ timeout(Duration(10, TimeUnit.SECONDS)),
      test("Amqp.declareQueuePassive checks if a queue exists") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val queueName      = QueueName(s"queue-$testAmqpSuffix")

        ZIO.scoped {
          for {
            channel <- amqpChannel
            _       <- channel.queueDeclare(queueName)
            q       <- channel.queueDeclarePassive(queueName)
            _       <- channel.queueDelete(queueName)
          } yield assertTrue(q.getQueue == QueueName.unwrap(queueName))
        }
      } @@ timeout(Duration(10, TimeUnit.SECONDS)),
      test(
        "Amqp.messageCounts returns the number of messages in a queue ready to be delivered to consumers"
      ) {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val queueName      = QueueName(s"queue-$testAmqpSuffix")

        ZIO.scoped {
          for {
            channel <- amqpChannel
            _       <- channel.queueDeclare(queueName)
            before  <- channel.messageCount(queueName)
            _       <- channel.publish(
                         ExchangeName(""),
                         "ping".getBytes(StandardCharsets.UTF_8),
                         RoutingKey(QueueName.unwrap(queueName))
                       )
            after   <- channel.messageCount(queueName).delay(1.second)
            _       <- channel.queueDelete(queueName)
          } yield assertTrue(before -> after == 0L -> 1L)
        }
      } @@ withLiveClock @@ timeout(Duration(10, TimeUnit.SECONDS)),
      test("Amqp.consumerCounts the number of consumers on a queue") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val queueName      = QueueName(s"queue-$testAmqpSuffix")

        ZIO.scoped {
          for {
            channel <- amqpChannel
            _       <- channel.queueDeclare(queueName)
            before  <- channel.consumerCount(queueName)
            fiber   <- channel.consume(queueName, ConsumerTag("tag")).runDrain.fork
            after   <- channel.consumerCount(queueName).delay(1.second)
            _       <- fiber.interrupt
            _       <- channel.queueDelete(queueName)
          } yield assertTrue(before -> after == 0L -> 1L)
        }
      } @@ withLiveClock @@ timeout(Duration(10, TimeUnit.SECONDS))
    ).provideShared(
      new RabbitmqContainer().toLayer
    )
}
