package nl.vroste.zio.amqp
import java.net.URI

import com.rabbitmq.client.ConnectionFactory
import zio.ZIO
import zio.test._
import zio.stream.ZTransducer

object AmqpClientSpec extends DefaultRunnableSpec {

  override def spec =
    suite("AmqpClientSpec")(
      testM("Amqp.consume delivers messages") {
        val factory = new ConnectionFactory()
        val uri     = URI.create(Option(System.getenv("AMQP_SERVER_URI")).getOrElse("amqp://guest:guest@localhost:5672"))
        println(uri)
        factory.setUri(uri)

        (Amqp
          .connect(factory)
          .tapM(_ => ZIO(println("Connected!"))) >>= Amqp.createChannel)
          .tapM(_ => ZIO(println("Created channel!")))
          .use { channel =>
            for {
              _ <- channel.queueDeclare("queue1")
              _ <- channel.exchangeDeclare("exchange1", ExchangeType.Fanout)
              _ <- channel.queueBind("queue1", "exchange1", "myroutingkey")
              _ <- channel.publish("exchange1", "message1".getBytes)
              _ <- channel.publish("exchange1", "message2".getBytes)
              _ <- channel
                     .consume(queue = "queue1", consumerTag = "test")
                     .mapM { record =>
                       println(s"${record.getEnvelope.getDeliveryTag}: ${new String(record.getBody)}")
                       ZIO.succeed(record.getEnvelope.getDeliveryTag)
                     }
                     .take(2)
                     .aggregate(ZTransducer.collectAllN[Long](100))
                     .map(_.last)
                     .mapM { tag =>
                       println(s"At tag: ${tag}")
                       channel.ack(tag)
                     }
                     .runDrain
            } yield assertCompletes
          }
      }
    )
}
