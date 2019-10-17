package com.fyber.vault.client

import akka.actor.ActorSystem
import com.fyber.vault.configuration.VaultClient
import com.fyber.vault.engines.infra.{BasicEngineInfo, EngineConfiguration, Engines}
import com.fyber.vault.engines.{AWS, Database, KV}
import com.typesafe.config.{Config, ConfigFactory}


/**
  * The Secrets client class
  * This client exposes a DSL for the various Hashicorp-Vault operaionts
  * Use a constructor apply method (from the companion object, see description) to imitate a Secrets object
  * Secrets read it's configuration by an existing application.conf file, Typesafe config object or From Consul.
  * Example of Use in a Scala environment
  * {{{
  *    val secrets = Secrets()
  *    val credentials = secrets.engines.aws.getCredentials
  *    val accessKey = credentials.accessKey
  *    val secretKey = credentials.secretKey
  * }}}
  * The recommended way to initiate Secrets without providing an application.conf (maybe from a non-scala environment)
  * described in the appropriate apply constructor methods of the companion object
  *
  * @param actorSystem - used for Akka-scheduling, when auto-lease renew is activated
  * @param engines     - supported secret engines
  */
case class Secrets(actorSystem: ActorSystem, engines: Engines)

/**
  * The Secrets client class companion object
  */
object Secrets {

  /**
    * This is a default actor-system is for Akka-scheduling only.
    * Would be in use when auto-lease renew is activated
    */
  lazy val defaultSystem = ActorSystem("secrets")

  /**
    * Default discovery uses the machine's local consul-client.
    * Usage: fallback for finding the vault servers and for secret-engines configuration
    */
  lazy val discovery = com.fyber.consul.Discovery()

  /**
    * Use the default apply method in scala environment, where the the Vault configuration relies in the application.conf file
    * Use the default if you're interested in an automatic token renewal, uses the default dispatcher, or if you're not interested in auto renewal.
    * The configuration structure should be as follows:
    * ``
    * {
    * secrets {
    * token = //A valid token with the appropriate role to create the service credentials
    * host = //Vault server host url. If not specified, will be taken from consul
    * port = (8200),
    * protocol = "https" //if missing has a default fallback to http
    * readTimeout =  //read credentials timeout in seconds
    * engines { // supported secret engines
    *   aws {
    *     keypath = // path for the role, required for creating the service credentials
    *     retry{ // retry in case of failure while accessing vault server
    *     retries{
    *     maxRetries =
    *       retryIntervalMilliseconds = (Seconds)
    *       }
    *     }
    *     renew{
    *     scheduled = false, //automatic token renew
    *     scheduleDuration = (Seconds)
    *     increment = (Seconds)
    *     }
    * }
    * database {
    *   keypath = "" //""/creds/mysql-read-only"
    *   retry {
    *     retries {
    *       maxRetries = 3
    *         retryIntervalMilliseconds = 1000
    *       }
    *     }
    *   renew {
    *     scheduled = false,
    *     scheduleDuration = 10 seconds
    *     increment = 43200
    *     }
    *   }
    * }
    * }
    * ``
    */
  def apply(): Secrets = {
    Secrets(loadDefaultConfig)
  }

  /**
    * Create a Secrets client from config, with a given actor system (the renewable mechanism will use the system's dispatcher for scheduling)
    *
    * @param maybeActorSystem
    */
  def apply(maybeActorSystem: ActorSystem): Secrets = {
    Secrets(loadDefaultConfig, Some(maybeActorSystem))
  }

  /**
    * Create a Secrets client from a given config, and a given actor system (the renewable mechanism will use the system's dispatcher for scheduling)
    *
    * @param config           - configuration for vault server, as described above
    * @param maybeActorSystem - the auto-renewal will use the actor-system's dispatcher, if provided.
    */

  def apply(config: Config, maybeActorSystem: Option[ActorSystem] = None, maybeMonitor: Option[VaultMonitorEngine] = None): Secrets = {

    /*Monitor.client = config.getString("client")*/
    implicit val system = maybeActorSystem.getOrElse(defaultSystem)
    implicit val monitor = if (config.getBoolean("monitoring")) maybeMonitor.getOrElse(KamonMonitorEngine.start(ConfigFactory.load("kamon.conf"))) else new MockMonitor
    val vaultClient = VaultClient(config)
    val aws = /*todo use dummy engine if not supported*/ AWS(EngineConfiguration(config.getConfig("engines.aws")), vaultClient)
    val database = /*todo use dummy engine if not supported*/ Database(EngineConfiguration(config.getConfig("engines.database")), vaultClient)
    val kv = /*todo use dummy engine if not supported*/ KV(EngineConfiguration(config.getConfig("engines.database")), vaultClient)
    new Secrets(system, Engines(aws, database, kv))
  }

  /**
    * Use this Secrets constructor directly to get client with a given Engines
    *
    * @param engines - supported secret engines
    *
    */
  def apply(engines: Engines): Secrets = {
    Secrets(engines, None)
  }

  /**
    * Use this Secrets constructor directly to get client with a given Engines
    *
    * @param engines          - supported secret engines
    * @param maybeActorSystem - the renewable mechanism will use the system's dispatcher for scheduling.
    *
    */
  def apply(engines: Engines, maybeActorSystem: Option[ActorSystem]): Secrets = {

    implicit val system = maybeActorSystem.getOrElse(defaultSystem)

    new Secrets(system, engines)
  }


  private def loadDefaultConfig = {
    ConfigFactory.load().withFallback(ConfigFactory.load("application.conf")).getConfig("secrets")
  }
}