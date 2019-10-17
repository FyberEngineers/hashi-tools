package com.fyber.vault.auth

import akka.actor.ActorSystem
import com.bettercloud.vault.Vault
import com.fyber.vault.client.Secrets
import com.fyber.vault.configuration.VaultConfigurationProperties.{Policy, Role, Token}
import com.fyber.vault.engines.infra.EngineConfiguration.Renew
import com.fyber.vault.engines.infra.SecretEntity
import com.typesafe.config.Config
import org.reflections.Reflections

import scala.collection.JavaConversions._
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Created by dani on 05/03/19.
  */
trait Auth extends SecretEntity{
  def createSecret(vault: Vault, policy: Policy, role: Role, config: Config)(implicit actorSystem: ActorSystem = Secrets.defaultSystem): Token
}
object Auth{
  private[vault] lazy val authMethods: Map[String, Auth] =
    new Reflections("com.fyber").getSubTypesOf(classOf[Auth]) filterNot (_ isInterface) map {
      cls => cls.getSimpleName.dropRight(1).capitalize -> cls.getField("MODULE$").get(null).asInstanceOf[Auth]
    } toMap
  def renewalConfiguration(config: Config) = Renew(config.getBoolean("renew.scheduled"), Duration(config.getString("renew.scheduleDuration")).asInstanceOf[FiniteDuration], config.getInt("renew.increment"))
}

