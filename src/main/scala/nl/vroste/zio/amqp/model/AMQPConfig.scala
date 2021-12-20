package nl.vroste.zio.amqp.model
import zio.{ durationInt, Duration }

import java.net.URI

case class AMQPConfig(
  user: String,
  password: String,
  vhost: String,
  heartbeatInterval: Duration,
  ssl: Boolean,
  host: String,
  port: Short,
  connectionTimeout: Duration
) {
  def toUri: AmqpUri =
    AmqpUri(
      new URI(
        s"amqp${if (ssl) "s" else ""}://$user:$password@$host:$port/$vhost?heartbeat=${heartbeatInterval.getSeconds}&connection_timeout=${connectionTimeout.toMillis}"
      )
    )
}
object AMQPConfig {
  lazy val default = AMQPConfig(
    user = "guest",
    password = "guest",
    vhost = "/",
    heartbeatInterval = 10.seconds,
    ssl = false,
    host = "localhost",
    port = 5672,
    connectionTimeout = 10.seconds
  )

}
