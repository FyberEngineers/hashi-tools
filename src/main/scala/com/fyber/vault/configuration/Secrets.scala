package com.fyber.vault.configuration

import akka.actor.ActorSystem
import com.bettercloud.vault.{Vault, VaultConfig, VaultException}
import com.fyber.vault.auth.{Auth, VaultToken}
import com.fyber.vault.client.{Monitor, Secrets, VaultMonitorEngine}
import com.fyber.vault.configuration.VaultConfigurationProperties._
import com.fyber.vault.engines.infra.EngineConfiguration.Renew
import com.fyber.vault.engines.infra.Renewable
import com.typesafe.config.Config

import scala.util.{Failure, Success, Try}

/**
  * Created by dani on 14/08/18.
  */

object VaultClient extends Renewable {
  def getAddress(protocol: Protocol = Protocol.Http,
                 host: Option[String] = None,
                 port: String = "8200",
                 vaultConsulServiceName: Option[String] = None) =
    Address(s"$protocol://${host.getOrElse(Secrets.discovery.getHealthServices(vaultConsulServiceName.get) /*todo configuration*/ .head)}:$port")

  def apply(vaultConfiguration: Config)(implicit vaultMonitorEngine: VaultMonitorEngine, actorSystem: ActorSystem): Vault = {
    implicit val monitor: Monitor = new Monitor(vaultMonitorEngine)
    val loginConfiguration: LoginConfiguration = LoginConfiguration(Some(vaultConfiguration))
    val vaultClient = VaultClient(loginConfiguration)
    try {
      if (loginConfiguration.vaultTokenRenewalConfig.scheduled) {
        scheduleRenew(loginConfiguration.vaultTokenRenewalConfig.duration, () => {
            Try {
              vaultClient.auth.renewSelf(loginConfiguration.vaultTokenRenewalConfig.increment)
            } match {
              case Success(r) =>monitor.recordLeaseRenew("vault", "failed"); r.getAuthClientToken
              case Failure(e) =>
                e match {
                  case v: VaultException =>
                    //todo: log error, indicates vault exception
                    monitor.recordLeaseRenew("vault", "failed")
                    throw v
                  case e: Exception =>
                    //todo: log error
                    monitor.recordLeaseRenew("vault", "failed")
                    throw e
                }
            }
        })
      }
    } catch {
      case e: Exception =>
      //todo handle
      throw e
    }
    vaultClient
  }

  def apply(loginConfiguration: LoginConfiguration): Vault = {
    val config =
      new VaultConfig()
        .readTimeout(loginConfiguration.readTimeout)
        .address(loginConfiguration.address.vaultAddress)
        .token(loginConfiguration.token.vaultToken)
        .build()
    new Vault(config)
  }

}

case class LoginConfiguration(address: Address, token: Token, vaultTokenRenewalConfig: Renew, readTimeout: Int = 5)

object LoginConfiguration {

  def apply(maybeVaultConfiguration: Option[Config])(implicit monitor: Monitor, actorSystem: ActorSystem): LoginConfiguration = {

    //todo remove optional config
    maybeVaultConfiguration.map(vaultConfiguration => {

      val address = VaultClient.getAddress(
        protocol = Protocol.all.getOrElse(vaultConfiguration.getString("protocol").toLowerCase, Protocol.Http),
        host = vaultConfiguration.getString("host"),
        port = vaultConfiguration.getString("port"),
        vaultConsulServiceName = vaultConfiguration.getString("vaultConsulServiceName")
      )

      val readTimeout = vaultConfiguration.getInt("readTimeout")
      val vaultAuthConfiguration = vaultConfiguration.getConfig("auth")
      val vaultTokenRenewalConfig = Auth.renewalConfiguration(vaultAuthConfiguration)
      val method: Auth = {val methodConfig = vaultAuthConfiguration.getString("method"); if (methodConfig == VaultToken.name) VaultToken else Auth.authMethods(vaultAuthConfiguration.getString("method"))}
      val methodProperties = vaultAuthConfiguration.getConfig("properties")
      val policy = vaultAuthConfiguration.getString("vaultPolicy")
      val role = vaultAuthConfiguration.getString("vaultRole")
      val token = {
        Try {
          method.createSecret(new Vault(new VaultConfig()
            .readTimeout(readTimeout)
            .address(address.vaultAddress)
            .build()), policy, role, methodProperties)
        } match {
          case Success(r) => monitor.recordLeaseCreate("vault", "success"); r
          case Failure(e) =>
            e match {
              case v: VaultException =>
                //todo: log error, indicates vault exception
                monitor.recordLeaseCreate("vault", "failed")
                throw v
              case e: Exception =>
                //todo: log error
                monitor.recordLeaseCreate("vault", "failed")
                throw e
            }
        }
      }
      new LoginConfiguration(
        address = address,
        readTimeout = readTimeout,
        token = token,
        vaultTokenRenewalConfig = vaultTokenRenewalConfig
      )
    }).orNull     //todo remove optional config
  }
}

/**
  * Requires 'address' and 'token'
  */

object VaultConfigurationProperties {

  implicit class Address(val vaultAddress: String) extends AnyVal

  implicit class Token(val vaultToken: String) extends AnyVal

  implicit class KeyName(val name: String) extends AnyVal

  implicit class Policy(val policy: String) extends AnyVal

  implicit class Role(val role: String) extends AnyVal

}

trait Protocol {
  def show: String = toString.toLowerCase
}

object Protocol {

  case object Http extends Protocol

  case object Https extends Protocol

  val all: Map[String, Protocol] = List(Http, Https).map(p => p.show -> p).toMap
}