package com.fyber.vault.auth

import akka.actor.ActorSystem
import com.bettercloud.vault.Vault
import com.fyber.vault.configuration.VaultConfigurationProperties.{Policy, Role, Token}
import com.typesafe.config.Config

/**
  * Created by dani on 07/03/19.
  */
object VaultToken extends Auth{

  override val name: String = "VaultToken"

  override def createSecret(vault: Vault, policy: Policy, role: Role, config: Config)(implicit actorSystem: ActorSystem): Token = config.getString("token")


}
