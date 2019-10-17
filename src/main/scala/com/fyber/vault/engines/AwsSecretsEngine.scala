package com.fyber.vault.engines

import akka.actor.ActorSystem
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.AmazonIdentityManagementException
import com.bettercloud.vault.Vault
import com.fyber.vault.client.{Secrets, VaultMonitorEngine}
import com.fyber.vault.engines.AWS.{AWSCredentials, AccessKey, SecretKey}
import com.fyber.vault.engines.infra.EngineConfiguration.{KeyPath, Mount}
import com.fyber.vault.engines.infra._
import monix.execution.Scheduler

import scala.concurrent.Future

/**
  * Created by dani on 20/08/18.
  */

trait AWSSecretsEngine extends SecretsEngine with SecretsEnginesRenewable {

  override type C = AWSCredentials

  override def getCredentials(mount: Mount): C
  override def getCredentials: C = getCredentials(name)
  def getCredentialsAndWait(mount: Mount = name, maxWaitMs: Int = 10000): C = {
    val awsCredentials = getCredentials(mount)
    AWS.waitUntillReady(awsCredentials, maxWaitMs)
  }
  /*
  This blocks!!!
   */
  def getCredentialsAndWaitAsync(mount: Mount, maxWaitMs: Int = 10000)(implicit actorSystem: ActorSystem = Secrets.defaultSystem): Future[C] = {
    getCredentialsAsyncForMount(mount).map(a => AWS.waitUntillReady(a, maxWaitMs))(Scheduler(actorSystem.dispatcher))
  }
}

final class AWS(protected val engineConfiguration: EngineConfiguration, private val vaultClient: Vault, override protected val monitorEngine: VaultMonitorEngine)(implicit actorSystem: ActorSystem = Secrets.defaultSystem) extends AWSSecretsEngine {
  private val vaultAws = vaultClient.withRetries(engineConfiguration.retries.maxRetries, engineConfiguration.retries.retryIntervalMilliseconds)
  override protected val engineVaultClient: EngineVaultClient = EngineVaultClient(vaultAws)

  override val name: String = AWS.name

  override def getCredentials(mount: Mount): C = {
    val credentials = credentialsMap(mount)
    AWSCredentials(AccessKey(credentials("access_key")), SecretKey(credentials("secret_key")))
  }

  override def renew(mount: Mount): Unit = super.renew(mount, engineConfiguration.renew.increment)



}

abstract class VaultAWSException(s:String) extends Exception(s){}
case class AWSUserException(s:String) extends VaultAWSException(s){}

object AWS {

  private[engines] def waitUntillReady(awsCredentials: AWSCredentials, maxWait: Int = 10000): AWSCredentials = {

    val iam: AmazonIdentityManagementClient = new AmazonIdentityManagementClient(new BasicAWSCredentials(awsCredentials.accessKey.key, awsCredentials.secretKey.key))

    try {
      iam.getUser
      awsCredentials
    }
    catch {
      case e: AmazonIdentityManagementException => {
        if (e.getErrorCode == "InvalidClientTokenId" && maxWait > 0) {
          Thread.sleep(500)
          waitUntillReady(awsCredentials, maxWait - 500)
        } else if (maxWait == 0) throw AWSUserException("User was not ready on time")
        else awsCredentials
      }
      case ex: Exception => throw ex
    }
  }

  def apply(engineConfiguration: EngineConfiguration, vaultClient: Vault)
           (implicit actorSystem: ActorSystem = Secrets.defaultSystem, monitorEngine: VaultMonitorEngine): AWS =
    new AWS(engineConfiguration, vaultClient, monitorEngine)

  val name: String = "aws"

  case class AWSCredentials(accessKey: AccessKey, secretKey: SecretKey) extends Credentials

  case class AccessKey(key: String) extends AnyVal

  case class SecretKey(key: String) extends AnyVal

  case class LeaseId(id: String) extends AnyVal

}


object AWSNotSupported extends AWSSecretsEngine {
  override protected val engineConfiguration: EngineConfiguration = ???
  override val name: String = ???
  override protected lazy val engineVaultClient: EngineVaultClient = ???
  override protected lazy val keyPath: KeyPath = ???
  override def getCredentials: AWSCredentials = ???
  override protected val monitorEngine: VaultMonitorEngine = ???
  override def getCredentials(mount: Mount): AWSCredentials = ???
  override def renew(mount: Mount): Unit = ???
}


