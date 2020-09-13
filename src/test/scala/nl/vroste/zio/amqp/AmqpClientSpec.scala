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
        factory.setUri(URI.create(System.getenv("AMQP_SERVER_URI")))

        val queue = System.getenv("AMQP_QUEUE")

        (Amqp
          .connect(factory)
          .tapM(_ => ZIO(println("Connected!"))) >>= Amqp.createChannel)
          .tapM(_ => ZIO(println("Created channel!")))
          .use { channel =>
            for {
              _ <- channel.queueDeclare(queue)
              _ <- channel
                     .consume(queue = queue, consumerTag = "test")
                     .mapM { record =>
                       println(s"${record.getEnvelope.getDeliveryTag}: ${new String(record.getBody)}")
                       ZIO.succeed(record.getEnvelope.getDeliveryTag)
                     }
                     .take(200)
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
