package nl.vroste.zio.amqp
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._
import zio.{ durationInt, Clock, Duration, ZIO }

import nl.vroste.zio.amqp.connection.Connection
import nl.vroste.zio.amqp.connection.config.ConnectionConfig
import nl.vroste.zio.amqp.model._

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

object AmqpSpec extends DefaultRunnableSpec {
  val fallback = "amqp://guest:guest@0.0.0.0:5672"

  override def spec =
    suite("AmqpClientSpec")(
      test("Amqp.consume delivers messages") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val exchangeName   = ExchangeName(s"exchange-$testAmqpSuffix")
        val queueName      = QueueName(s"queue-$testAmqpSuffix")
        val message1       = UUID.randomUUID().toString
        val message2       = UUID.randomUUID().toString
        val messages       = Set(message1, message2)
        val uri            = URI.create(Option(System.getenv("AMQP_SERVER_URI")).getOrElse(fallback))

        Connection
          .managed(ConnectionConfig.uri(uri))
          .flatMap(_.channelManaged)
          .use { channel =>
            for {
              _      <- channel.queueDeclare(queueName)
              _      <- channel.exchangeDeclare(exchangeName, ExchangeType.Fanout)
              _      <- channel.queueBind(queueName, exchangeName, RoutingKey("myroutingkey"))
              _      <- channel.publish(exchangeName, message1.getBytes)
              _      <- channel.publish(exchangeName, message2.getBytes)
              bodies <- channel
                          .consume(queue = queueName, consumerTag = ConsumerTag("test"))
                          .take(2)
                          .runCollect
                          .tap { records =>
                            val tag = records.last.getEnvelope.getDeliveryTag
                            channel.ack(DeliveryTag(tag))
                          }
                          .map(_.map(r => new String(r.getBody)))
              _      <- channel.queueDelete(queueName)
              _      <- channel.exchangeDelete(exchangeName)
            } yield assert(messages)(equalTo(bodies.toSet))
          }
      } @@ timeout(Duration(10, TimeUnit.SECONDS)),
      test("Amqp.publish delivers messages with high concurrency") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val exchangeName   = ExchangeName(s"exchange-$testAmqpSuffix")
        val queueName      = QueueName(s"queue-$testAmqpSuffix")
        val numMessages    = 10000
        val messages       = (1 to numMessages).map(i => s"$i " + UUID.randomUUID.toString)
        val uri            = URI.create(Option(System.getenv("AMQP_SERVER_URI")).getOrElse(fallback))

        Connection
          .managed(ConnectionConfig.uri(uri))
          .flatMap(_.channelManaged)
          .use { channel =>
            for {
              _      <- channel.queueDeclare(queueName)
              _      <-
                channel.exchangeDeclare(
                  exchangeName,
                  // doesn't actually need to be a custom exchange type,
                  // just testing to make sure custom exchange types "work"
                  ExchangeType.Custom("fanout")
                )
              _      <- channel.queueBind(queueName, exchangeName, RoutingKey("myroutingkey"))
              _      <-
                ZIO.foreachParDiscard(0 until numMessages)(i => channel.publish(exchangeName, messages(i).getBytes))
              bodies <- channel
                          .consume(queue = queueName, consumerTag = ConsumerTag("test"))
                          .take(numMessages.toLong)
                          .runCollect
                          .tap { records =>
                            val tag = records.last.getEnvelope.getDeliveryTag
                            channel.ack(DeliveryTag(tag))
                          }
                          .map(_.map(r => new String(r.getBody)))
              _      <- channel.queueDelete(queueName)
              _      <- channel.exchangeDelete(exchangeName)
            } yield assert(messages.toSet)(equalTo(bodies.toSet))
          }
      } @@ timeout(Duration(10, TimeUnit.SECONDS)),
      test("Amqp.declareQueuePassive checks if a queue exists") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val queueName      = QueueName(s"queue-$testAmqpSuffix")
        val uri            = URI.create(Option(System.getenv("AMQP_SERVER_URI")).getOrElse(fallback))

        Connection
          .managed(ConnectionConfig.uri(uri))
          .flatMap(_.channelManaged)
          .use { channel =>
            for {
              _ <- channel.queueDeclare(queueName)
              q <- channel.queueDeclarePassive(queueName)
              _ <- channel.queueDelete(queueName)
            } yield assert(q.getQueue)(equalTo(QueueName.unwrap(queueName)))
          }
      } @@ timeout(Duration(10, TimeUnit.SECONDS)),
      test("Amqp.messageCounts returns the number of messages in a queue ready to be delivered to consumers") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val queueName      = QueueName(s"queue-$testAmqpSuffix")
        val uri            = URI.create(Option(System.getenv("AMQP_SERVER_URI")).getOrElse(fallback))

        Connection
          .managed(ConnectionConfig.uri(uri))
          .flatMap(_.channelManaged)
          .use { channel =>
            for {
              _      <- channel.queueDeclare(queueName)
              before <- channel.messageCount(queueName)
              _      <- channel.publish(
                          ExchangeName(""),
                          "ping".getBytes(StandardCharsets.UTF_8),
                          RoutingKey(QueueName.unwrap(queueName))
                        )
              after  <- channel.messageCount(queueName).delay(1.second)
              _      <- channel.queueDelete(queueName)
            } yield assert(before -> after)(equalTo(0L -> 1L))
          }
          .provideSomeLayer(Clock.live)
      } @@ timeout(Duration(10, TimeUnit.SECONDS)),
      test("Amqp.consumerCounts the number of consumers on a queue") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val queueName      = QueueName(s"queue-$testAmqpSuffix")
        val uri            = URI.create(Option(System.getenv("AMQP_SERVER_URI")).getOrElse(fallback))

        Connection
          .managed(ConnectionConfig.uri(uri))
          .flatMap(_.channelManaged)
          .use { channel =>
            for {
              _      <- channel.queueDeclare(queueName)
              before <- channel.consumerCount(queueName)
              fiber  <- channel.consume(queueName, ConsumerTag("tag")).runDrain.fork
              after  <- channel.consumerCount(queueName).delay(1.second)
              _      <- fiber.interrupt
              _      <- channel.queueDelete(queueName)
            } yield assert(before -> after)(equalTo(0L -> 1L))
          }
          .provideSomeLayer(Clock.live)
      } @@ timeout(Duration(10, TimeUnit.SECONDS))
    )
}
