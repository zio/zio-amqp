# ZIO AMQP

ZIO AMQP is a ZIO-based wrapper around the RabbitMQ client. It provides a streaming interface to AMQP queues and helps to prevent you from shooting yourself in the foot with thread-safety issues. 


## Usage 

Add to your build.sbt:

```scala
libraryDependencies += "nl.vroste" %% "zio-amqp" % "0.0.1"
```

### Consuming

The example below creates a connection to an AMQP server and then creates a channel. Both are created as Managed resources, which means they are closed automatically after using even in the face of errors.

The example then creates a stream of the messages consumed from a queue named `"queueName"`. Each received message is acknowledged back to the AMQP server.

```scala
import com.rabbitmq.client.ConnectionFactory
import nl.vroste.zio.amqp._
import java.net.URI
import zio._
import zio.blocking._
import zio.console._

val channelM: ZManaged[Blocking, Throwable, Channel] = for { 
  connection <- Amqp.connect(URI.create("amqp://my_amqp_server_uri"))
  channel <- Amqp.createChannel(connection)
} yield channel


val effect: ZIO[Blocking with Console, Throwable, Unit] = channelM.use { channel =>
    channel
    .consume(queue = "queueName", consumerTag = "test")
    .mapM { record =>
      val deliveryTag = record.getEnvelope.getDeliveryTag
      putStrLn(s"Received ${deliveryTag}: ${new String(record.getBody)}") *> 
        channel.ack(deliveryTag)
    }
    .take(5)
    .runDrain
}

```

See the [ZIO documentation](https://zio.dev/docs/overview/overview_running_effects#defaultruntime) for more information on how to run this effect or integrate with an existing application. 
