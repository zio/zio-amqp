package nl.vroste.zio.amqp

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.dimafeng.testcontainers.RabbitMQContainer
import com.rabbitmq.client.ConnectionFactory
import nl.vroste.zio.amqp.model._
import org.testcontainers.utility.DockerImageName
import zio.test.TestAspect.{timeout, withLiveClock}
import zio.test._
import zio.testcontainers.ZLayerTestContainerOps
import zio.{durationInt, Duration, Scope, ZIO, ZLayer}

object AmqpClientSpec extends ZIOSpecDefault {
  private val rabbitmqContainer = ZLayer.fromTestContainer {
    new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.11"))
  }

  private def amqpChannel: ZIO[Scope with RabbitMQContainer, Throwable, Channel] =
    for {
      container  <- ZIO.service[RabbitMQContainer]
      _          <- ZIO.logInfo(s"RabbitMQ URI:  ${container.amqpUrl}")
      factory     = {
        val factory = new ConnectionFactory()
        factory.setUri(container.amqpUrl)
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
                           ZIO.logInfo(
                             s"${record.getEnvelope.getDeliveryTag}: ${new String(record.getBody)}"
                           ) *> ZIO.succeed(record)
                         }
                         .take(2)
                         .runCollect
                         .tap { records =>
                           val tag = records.last.getEnvelope.getDeliveryTag
                           ZIO.logInfo(s"At tag: $tag") *> channel.ack(DeliveryTag(tag))
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
            _       <- ZIO.foreachParDiscard(0 until numMessages)(i =>
                         channel.publish(exchangeName, messages(i).getBytes)
                       )
            bodies  <- channel
                         .consume(queue = queueName, consumerTag = ConsumerTag("test"))
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
      rabbitmqContainer
    )
}
