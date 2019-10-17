package com.fyber.vault.engines.infra

import akka.actor.ActorSystem
import com.bettercloud.vault.Vault
import com.bettercloud.vault.response.{LogicalResponse, VaultResponse}
import com.fyber.vault.client.Secrets
import com.fyber.vault.engines._
import com.fyber.vault.engines.infra.EngineConfiguration.{KeyPath, Mount, Renew, Retries}
import com.typesafe.config.Config
import monix.execution.Scheduler

import scala.collection.JavaConversions._
import scala.collection.mutable.{Map => MMap}
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

/**
  * Created by dani on 20/08/18.
  */
trait SecretsEngine extends TokenRecorder with SecretEntity {

  this: SecretsEnginesRenewable =>

  type C <: Credentials


  def getCredentials(mount: Mount): C
  def getCredentials: C = getCredentials(name)
  def getCredentialsAsyncForMount(mount: Mount)(implicit actorSystem: ActorSystem = Secrets.defaultSystem): Future[C] = Future(getCredentials(mount))(Scheduler(actorSystem.dispatcher))
  def getCredentialsAsync(implicit actorSystem: ActorSystem = Secrets.defaultSystem): Future[C] = Future(getCredentials)(Scheduler(actorSystem.dispatcher))

  protected val engineConfiguration: EngineConfiguration
  val name: String
  protected val engineVaultClient: EngineVaultClient
  protected lazy val keyPath: KeyPath = engineConfiguration.keyPath
  private val mountToSecret: MMap[Mount, LogicalResponse] = new scala.collection.mutable.HashMap[Mount, LogicalResponse]()

  protected def credentialsMap(mount: Mount)(implicit actorSystem: ActorSystem): Map[String, String] ={
    val credentials: Map[String, String] = readAll(mount)
    if (engineConfiguration.renew.scheduled) scheduleRenew(engineConfiguration.renew.duration, mount)
    credentials
  }

  protected def createSecret(mount: Mount): LogicalResponse = tryToken(Try {
    mountToSecret.getOrElse(mount, {
      val secret = engineVaultClient.vault.logical().read(s"${mount.mount}${keyPath.vaultPath}")
      mountToSecret.put(mount, secret)
      secret
    })
  }, successMetric = recordLeaseCreateSucceed, failedMetric = recordLeaseCreateFailed)


  protected def readAll(mount: Mount): Map[String, String] = {
    createSecret(mount).getData.toMap
  }

  protected def renew(mount: Mount, increment: Int): VaultResponse = {
    tryToken(Try {
      engineVaultClient.vault.leases().renew(mountToSecret(mount).getLeaseId, increment)
    }, successMetric = recordLeaseRenewSucceed, failedMetric = recordLeaseRenewFailed)
  }
}


trait Renewable {


  protected def scheduleRenew(duration: FiniteDuration, f: () => Unit)(implicit actorSystem: ActorSystem): Unit = {
    import actorSystem.dispatcher
    actorSystem.scheduler.schedule(duration, duration) {
      f()
    }
  }
}

trait SecretsEnginesRenewable extends Renewable {
  def renew(mount: Mount): Unit

  protected def scheduleRenew(duration: FiniteDuration, mount: Mount)(implicit actorSystem: ActorSystem): Unit = {
    import actorSystem.dispatcher
    actorSystem.scheduler.schedule(duration, duration) {
      renew(mount)
    }
  }
}



object EngineConfiguration {

  case class Renew(scheduled: Boolean, duration: FiniteDuration, increment: Int)

  case class Retries(maxRetries: Int = 3, retryIntervalMilliseconds: Int = 1500)

  implicit class KeyPath(val vaultPath: String) extends AnyVal

  implicit class Mount(val mount: String) extends AnyVal


  def apply(config: Config): EngineConfiguration = {
    val retriesConfig = Try {
      config.getConfig("retries")
    }.toOption
      .map(config => Retries(maxRetries = config.getString("maxRetries").toInt, retryIntervalMilliseconds = config.getString("retryIntervalMilliseconds").toInt))
      .getOrElse(Retries())
    val key = config.getString("keypath")
    val mount = Try {
      config.getString("mount")
    }.toOption map Mount
    EngineConfiguration(keyPath = key, retries = retriesConfig, renew = Renew(config.getBoolean("renew.scheduled"), Duration(config.getString("renew.scheduleDuration")).asInstanceOf[FiniteDuration], config.getInt("renew.increment")))
  }
}

case class Engines(aws: AWSSecretsEngine = AWSNotSupported, database: DatabaseEngine = DatabaseNotSupported, kv: KvSecretsEngine)


object Engines {
  /**
    * This create secret engines from Consul Kv store (rather then typesafe config)
    * The consul kv structure should be as follows:
    * |-- vault (vault root folder)
    * |   |-- client
    * |   |   |-- clientA
    * |   |   |   |-- engines (secret engines)
    * |   |   |   |   |-- engineA (i.e. aws)
    * |   |   |   |   |   |-- renew (for renewing the token automatically. The value indicates whether to enable auto renew, duration and ttl increment, in seconds.
    * For example, the vaule: true,1800,3600 means, auto renew is active and performed every 1/2 hour, increment the ttl by 1 hour. )
    * |   |   |   |   |   |-- retries (value indicates number of max retries and the interval among them in millis. i.e (3, 1000) means up to 3 tries, with a delay of a second)
    * |   |   |   |   |   |-- roles (vault role, for example: /creds/ec2-allow)
    * |   |   |   |   |-- engineB
    * |   |   |-- clientB
    * ....x
    *
    * @param engines - supported secret engines
    *
    */
  private def getEngine[E <: SecretsEngine](name: String, ctor: BasicEngineInfo => E)(engines: Set[BasicEngineInfo]): Option[E] = {
    engines.find(a => a.name.equals(name)).map(info => ctor(info))
  }
}

case class BasicEngineInfo(name: String, token: String)

case class EngineVaultClient(vault: Vault) extends VaultClientT

case class EngineConfiguration(keyPath: KeyPath, retries: Retries, renew: Renew)

trait VaultClientT {
  val vault: Vault
}

trait Credentials