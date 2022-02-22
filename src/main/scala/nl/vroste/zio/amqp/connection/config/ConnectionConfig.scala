package nl.vroste.zio.amqp.connection.config

import com.rabbitmq.client.ConnectionFactory

import nl.vroste.zio.amqp.connection.config.ConnectionConfig.ConnectionSetting

import java.net.URI

final case class ConnectionConfig(
  settings: List[ConnectionSetting] = Nil
) {
  private def withSetting(setting: ConnectionSetting): ConnectionConfig =
    copy(settings = setting +: settings)

  def withHost(host: String): ConnectionConfig =
    withSetting(ConnectionSetting.Host(host))

  def withPort(port: Int): ConnectionConfig =
    withSetting(ConnectionSetting.Port(port))

  def withUsername(username: String): ConnectionConfig =
    withSetting(ConnectionSetting.Username(username))

  def withPassword(password: String): ConnectionConfig =
    withSetting(ConnectionSetting.Password(password))

  def withUri(uri: String): ConnectionConfig =
    withSetting(ConnectionSetting.UriRaw(uri))

  def withUri(uri: URI): ConnectionConfig =
    withSetting(ConnectionSetting.Uri(uri))
}

object ConnectionConfig {

  def uri(uri: String): ConnectionConfig =
    ConnectionConfig().withUri(uri)

  def uri(uri: URI): ConnectionConfig =
    ConnectionConfig().withUri(uri)

  def hostPort(host: String, port: Int): ConnectionConfig =
    ConnectionConfig().withHost(host).withPort(port)

  sealed trait ConnectionSetting extends Product with Serializable {
    def apply(factory: ConnectionFactory): ConnectionFactory = {
      modification(factory)
      factory
    }

    def modification: ConnectionFactory => Unit
  }

  object ConnectionSetting {
    final case class Host(host: String) extends ConnectionSetting {
      override val modification = _.setHost(host)
    }

    final case class Port(port: Int) extends ConnectionSetting {
      override val modification = _.setPort(port)
    }

    final case class Username(username: String) extends ConnectionSetting {
      override val modification = _.setUsername(username)
    }

    final case class Password(password: String) extends ConnectionSetting {
      override val modification = _.setPassword(password)
    }

    final case class Uri(uri: URI) extends ConnectionSetting {
      override val modification = _.setUri(uri)
    }

    final case class UriRaw(uri: String) extends ConnectionSetting {
      override val modification = _.setUri(uri)
    }
  }
}
