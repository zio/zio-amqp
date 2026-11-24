package zio.amqp.model
import zio.{ Duration, durationInt }

import java.net.{ URI, URLEncoder }

/**
 * Settings of a connection to a RabbitMQ broker. `toUri` turns them into the AMQP URI that `Amqp.connect` uses.
 *
 * {{{
 * val local: AMQPConfig = AMQPConfig.default
 *
 * val remote: AMQPConfig =
 *   AMQPConfig.default.copy(host = "rabbit.example.com", user = "orders", password = "secret", ssl = true)
 * }}}
 *
 * @param user
 *   User name. It is written into the URI without percent-encoding
 * @param password
 *   Password. It is written into the URI without percent-encoding
 * @param vhost
 *   Virtual host, such as `/`. It is percent-encoded by `encodedVhost`
 * @param heartbeatInterval
 *   Heartbeat interval, sent to the broker in whole seconds. A fraction of a second is dropped, so 500 ms becomes 0,
 *   which turns heartbeats off
 * @param ssl
 *   True to connect with TLS. The URI scheme becomes `amqps`
 * @param host
 *   Host name of the broker
 * @param port
 *   Port of the broker
 * @param connectionTimeout
 *   Connection timeout, sent to the broker in milliseconds. A sub-millisecond part is dropped
 */
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

  /**
   * The `vhost` encoded with `java.net.URLEncoder` as UTF-8. For example, `/` becomes `%2F`, and a space becomes `+`.
   */
  val encodedVhost: String = URLEncoder.encode(vhost, "UTF-8")

  /**
   * The AMQP URI for these settings. The scheme is `amqps` when `ssl` is true, and `amqp` otherwise. The URI carries
   * the `heartbeat` parameter in whole seconds and the `connection_timeout` parameter in milliseconds.
   *
   * Throws a `java.net.URISyntaxException` when the URI can't be built, for example when the password contains a space.
   * The exception is thrown when the method is called.
   */
  def toUri: AmqpUri =
    AmqpUri(
      new URI(
        s"amqp${if (ssl) "s" else ""}://$user:$password@$host:$port/$encodedVhost?heartbeat=${heartbeatInterval.getSeconds}&connection_timeout=${connectionTimeout.toMillis}"
      )
    )
}

/**
 * The companion object of `AMQPConfig`, with the default settings.
 */
object AMQPConfig {

  /**
   * Settings for a broker on the local machine: user and password `guest`, host `localhost`, port 5672, virtual host
   * `/`, no TLS, and a connection timeout and heartbeat interval of 10 seconds each.
   */
  lazy val default: AMQPConfig = AMQPConfig(
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
