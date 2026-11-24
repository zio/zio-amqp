package zio.amqp.model
import zio.{ Duration, durationInt }

import java.net.{ URI, URLEncoder }

/**
 * Settings of a connection to a RabbitMQ broker. `toUri` turns them into the AMQP URI that `Amqp.connect(amqpConfig)`
 * uses.
 *
 * {{{
 * val local: AMQPConfig = AMQPConfig.default
 *
 * val remote: AMQPConfig =
 *   AMQPConfig.default.copy(host = "rabbit.example.com", port = 5671, user = "orders", password = "secret", ssl = true)
 * }}}
 *
 * @param user
 *   User name, used as it is in the URI
 * @param password
 *   Password, used as it is in the URI
 * @param vhost
 *   Virtual host, such as `/`. It is encoded with `encodedVhost`. A space in it reaches the broker as `+`, because the
 *   client does not decode `+` back to a space
 * @param heartbeatInterval
 *   Heartbeat interval, requested from the broker in whole seconds. A fraction of a second is dropped, so 500 ms
 *   becomes 0. The client and the broker use the smaller of their two values, and when one of them is 0 they use the
 *   other one
 * @param ssl
 *   True to connect with TLS. The URI scheme becomes `amqps`. The port is not changed, so set `port` to the broker's
 *   TLS port, such as 5671
 * @param host
 *   Host name of the broker
 * @param port
 *   Port of the broker. The type is `Short`, so a port above 32767 can't be given
 * @param connectionTimeout
 *   Timeout for opening the TCP connection, passed to the client in whole milliseconds. A sub-millisecond part is
 *   dropped, so 1500 µs becomes 1 ms
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
   * The URI is built when the method is called, and the user and password are placed in it as they are.
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
