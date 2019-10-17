package com.fyber.vault.engines

import akka.actor.ActorSystem
import com.bettercloud.vault.Vault
import com.bettercloud.vault.response.LogicalResponse
import com.fyber.vault.client.{Secrets, VaultMonitorEngine}
import com.fyber.vault.engines.KV.{KvCredentials, _}
import com.fyber.vault.engines.infra.EngineConfiguration.{KeyPath, Mount}
import com.fyber.vault.engines.infra._

import scala.collection.JavaConversions._
import scala.util.Try


trait KvSecretsEngine extends SecretsEngine with SecretsEnginesRenewable {

  override type C = KvCredentials

  def getCredentials(path: => Path): C
  protected def readValue(path: Path): Map[String, String] = {
    getValue(path).getData.toMap
  }
  def getCredentials(path: Path, key: Key): C

  private def getValue(path: Path): LogicalResponse = tryToken(Try {
    engineVaultClient.vault.logical().read(s"${KV.name}/${path.path}")
  }, successMetric = recordLeaseCreateSucceed, failedMetric = recordLeaseCreateFailed)

}

final class KV(protected val engineConfiguration: EngineConfiguration, private val vaultClient: Vault, override protected val monitorEngine: VaultMonitorEngine)
                    (implicit actorSystem: ActorSystem = Secrets.defaultSystem) extends KvSecretsEngine {

  override val name: String = KV.name
  private val vault = vaultClient.withRetries(engineConfiguration.retries.maxRetries, engineConfiguration.retries.retryIntervalMilliseconds)

  override protected val engineVaultClient: EngineVaultClient = EngineVaultClient(vault)

  private def valueMap(path: Path)(implicit actorSystem: ActorSystem): Map[String, String] ={
    val credentials: Map[String, String] = readValue(path)
    credentials
  }


  def getValue(path: Path, key:  Key): C = {
    val credentials = valueMap(path)
    KvCredentials(Value(credentials(key.key)))
  }

  override def renew(mount: Mount): Unit = ???

  def getCredentials(path: Path, key:  Key): KvCredentials = getValue(path, key)

  override def getCredentials(mount: Mount): KvCredentials = ???
  override def getCredentials(path: => Path): KvCredentials = ???
}


object KV {

  def apply(engineConfiguration: EngineConfiguration, vaultClient: Vault)
           (implicit actorSystem: ActorSystem = Secrets.defaultSystem, monitorEngine: VaultMonitorEngine): KV =
    new KV(engineConfiguration, vaultClient, monitorEngine)

  val name: String = "secret"

  case class KvCredentials(user: Value) extends Credentials
  case class Value(value: String) extends AnyVal
  case class Key(key: String) extends AnyVal
  case class Path(path: String) extends AnyVal

}


object KVNotSupported extends KvSecretsEngine {
  override protected val engineConfiguration: EngineConfiguration = ???
  override val name: String = ???
  override protected val engineVaultClient: EngineVaultClient = ???
  override protected lazy val keyPath: KeyPath = ???
  override protected val monitorEngine: VaultMonitorEngine = ???
  override def renew(mount: Mount): Unit = ???


  override def getCredentials(mount: Mount): KvCredentials = ???

  override def getCredentials(path: => Path): KvCredentials = ???

  override def getCredentials(path: Path, key: Key): KvCredentials = ???
}


