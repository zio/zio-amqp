package nl.vroste.zio.amqp.model
import java.net.URI

case class AMQPConfig(
  user: String,
  password: String,
  vhost: String,
  heartbeatInterval: Short,
  ssl: Boolean,
  host: String,
  port: Short,
  connectionTimeout: Short
) {
  def toUri: AmqpUri =
    AmqpUri(
      new URI(
        s"amqp${if (ssl) "s" else ""}://$user:$password@$host:$port/$vhost?heartbeat=${heartbeatInterval}&connection_timeout=${connectionTimeout}"
      )
    )
}
object AMQPConfig {
  lazy val default = AMQPConfig(
    user = "guest",
    password = "guest",
    vhost = "/",
    heartbeatInterval = 10,
    ssl = false,
    host = "localhost",
    port = 5672,
    connectionTimeout = 10
  )

}
