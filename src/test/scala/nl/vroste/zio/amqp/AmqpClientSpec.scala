package nl.vroste.zio.amqp
import com.rabbitmq.client.ConnectionFactory
import zio.ZIO
import zio.duration.Duration
import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test._

import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

object AmqpClientSpec extends DefaultRunnableSpec {

  override def spec =
    suite("AmqpClientSpec")(
      testM("Amqp.consume delivers messages") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val exchangeName   = s"exchange-$testAmqpSuffix"
        val queueName      = s"queue-$testAmqpSuffix"
        val message1       = UUID.randomUUID().toString
        val message2       = UUID.randomUUID().toString
        val messages       = Set(message1, message2)
        val factory        = new ConnectionFactory()
        val uri            = URI.create(Option(System.getenv("AMQP_SERVER_URI")).getOrElse("amqp://guest:guest@localhost:5672"))
        println(uri)
        factory.setUri(uri)

        (Amqp
          .connect(factory)
          .tapM(_ => ZIO(println("Connected!"))) >>= Amqp.createChannel)
          .tapM(_ => ZIO(println("Created channel!")))
          .use { channel =>
            for {
              _      <- channel.queueDeclare(queueName)
              _      <- channel.exchangeDeclare(exchangeName, ExchangeType.Fanout)
              _      <- channel.queueBind(queueName, exchangeName, "myroutingkey")
              _      <- channel.publish(exchangeName, message1.getBytes)
              _      <- channel.publish(exchangeName, message2.getBytes)
              bodies <- channel
                          .consume(queue = queueName, consumerTag = "test")
                          .mapM { record =>
                            println(s"${record.getEnvelope.getDeliveryTag}: ${new String(record.getBody)}")
                            ZIO.succeed(record)
                          }
                          .take(2)
                          .runCollect
                          .tap { records =>
                            val tag = records.last.getEnvelope.getDeliveryTag
                            println(s"At tag: $tag")
                            channel.ack(tag)

                          }
                          .map(_.map(r => new String(r.getBody)))
              _      <- channel.queueDelete(queueName)
              _      <- channel.exchangeDelete(exchangeName)
            } yield assert(messages)(equalTo(bodies.toSet))
          }
      } @@ timeout(Duration(10, TimeUnit.SECONDS)),
      testM("Amqp.publish delivers messages with high concurrency") {
        val testAmqpSuffix = s"AmqpClientSpec-${UUID.randomUUID().toString}"
        val exchangeName   = s"exchange-$testAmqpSuffix"
        val queueName      = s"queue-$testAmqpSuffix"
        val numMessages    = 10000
        val messages       = (1 to numMessages).map(i => s"$i " + UUID.randomUUID.toString)
        val factory        = new ConnectionFactory()
        val uri            = URI.create(Option(System.getenv("AMQP_SERVER_URI")).getOrElse("amqp://guest:guest@localhost:5672"))
        println(uri)
        factory.setUri(uri)

        (Amqp
          .connect(factory)
          .tapM(_ => ZIO(println("Connected!"))) >>= Amqp.createChannel)
          .tapM(_ => ZIO(println("Created channel!")))
          .use { channel =>
            for {
              _      <- channel.queueDeclare(queueName)
              _      <- channel.exchangeDeclare(exchangeName, ExchangeType.Fanout)
              _      <- channel.queueBind(queueName, exchangeName, "myroutingkey")
              _      <-
                ZIO.collectAllPar((0 until numMessages).map(i => channel.publish(exchangeName, messages(i).getBytes)))
              bodies <- channel
                          .consume(queue = queueName, consumerTag = "test")
                          .mapM { record =>
//                            println(s"consuming record ${new String(record.getBody)}")
                            ZIO.succeed(record)
                          }
                          .take(numMessages.toLong)
                          .runCollect
                          .tap { records =>
                            val tag = records.last.getEnvelope.getDeliveryTag
                            channel.ack(tag)

                          }
                          .map(_.map(r => new String(r.getBody)))
              _      <- channel.queueDelete(queueName)
              _      <- channel.exchangeDelete(exchangeName)
            } yield assert(messages.toSet)(equalTo(bodies.toSet))
          }
      } @@ timeout(Duration(10, TimeUnit.SECONDS))
    )
}
