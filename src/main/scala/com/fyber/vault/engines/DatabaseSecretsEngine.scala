package com.fyber.vault.engines

import akka.actor.ActorSystem
import com.bettercloud.vault.Vault
import com.fyber.vault.client.{Secrets, VaultMonitorEngine}
import com.fyber.vault.engines.Database.{DatabaseCredentials, _}
import com.fyber.vault.engines.infra.EngineConfiguration.{KeyPath, Mount}
import com.fyber.vault.engines.infra._


trait DatabaseEngine extends SecretsEngine with SecretsEnginesRenewable {

  override type C = DatabaseCredentials

  override def getCredentials(mount: Mount): C
  override def getCredentials: C = getCredentials(name)
}

final class Database(protected val engineConfiguration: EngineConfiguration, private val vaultClient: Vault, override protected val monitorEngine: VaultMonitorEngine)
                    (implicit actorSystem: ActorSystem = Secrets.defaultSystem) extends DatabaseEngine {

  override val name: String = Database.name
  private val vault = vaultClient.withRetries(engineConfiguration.retries.maxRetries, engineConfiguration.retries.retryIntervalMilliseconds)

  override protected val engineVaultClient: EngineVaultClient = EngineVaultClient(vault)


  override def getCredentials(mount: Mount): C = {
    val credentials = credentialsMap(mount)
    DatabaseCredentials(User(credentials("username")), Password(credentials("password")))
  }

  override def renew(mount: Mount): Unit = super.renew(mount, engineConfiguration.renew.increment)

}


object Database {

  def apply(engineConfiguration: EngineConfiguration, vaultClient: Vault)
           (implicit actorSystem: ActorSystem = Secrets.defaultSystem, monitorEngine: VaultMonitorEngine): Database =
    new Database(engineConfiguration, vaultClient, monitorEngine)

 /* def apply(client: String, token: String, vaultConsulServiceName: String)(implicit monitorEngine: VaultMonitorEngine): Database = {
    Database(client, token, 5, vaultConsulServiceName)
  }*/
/*
  def apply(client: String, token: String, timeout: Int, vaultConsulServiceName: String)(implicit monitorEngine: VaultMonitorEngine): Database = {
    new Database(createEngineConfigurationFromClient(client, name), createVaultFromEngineConfiguration(token = token, timeout = timeout, vaultConsulServiceName), monitorEngine)
  }*/


  val name: String = "database"

  case class DatabaseCredentials(user: User, password: Password) extends Credentials
  case class User(userName: String) extends AnyVal
  case class Password(password: String) extends AnyVal

}


object DatabaseNotSupported extends DatabaseEngine {
  override protected val engineConfiguration: EngineConfiguration = ???
  override val name: String = ???
  override protected val engineVaultClient: EngineVaultClient = ???
  override protected lazy val keyPath: KeyPath = ???
  override def getCredentials: DatabaseCredentials = ???
  override protected val monitorEngine: VaultMonitorEngine = ???
  override def getCredentials(mount: Mount): DatabaseCredentials = ???
  override def renew(mount: Mount): Unit = ???
}


