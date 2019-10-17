package com.fyber.vault.auth

import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Collections

import akka.actor.ActorSystem
import com.amazonaws.DefaultRequest
import com.amazonaws.auth.{AWS4Signer, AWSCredentials, AWSStaticCredentialsProvider, BasicSessionCredentials}
import com.amazonaws.http.HttpMethodName
import com.amazonaws.regions.Regions
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.bettercloud.vault.Vault
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fyber.vault.client.Secrets
import com.fyber.vault.configuration.VaultConfigurationProperties.{Policy, Role, Token}
import com.typesafe.config.Config
import org.apache.http.HttpHeaders
import org.springframework.util.Base64Utils

import scala.collection.JavaConversions._

/**
  * Created by dani on 05/03/19.
  */
object AWSIam extends Auth {

  val name: String = "AWSIam"
  private val OBJECT_MAPPER = new ObjectMapper
  private val REQUEST_BODY = "Action=GetCallerIdentity&Version=2011-06-15"
  private val REQUEST_BODY_BASE64_ENCODED = Base64Utils.encodeToString(REQUEST_BODY.getBytes)
  private val STS_ENDPOINT = "https://sts.amazonaws.com"

  lazy val STS = AWSSecurityTokenServiceClientBuilder.standard().withRegion(Regions.US_EAST_1).build()

  override def createSecret(vault: Vault, policy: Policy, role: Role, config: Config)(implicit actorSystem: ActorSystem = Secrets.defaultSystem): Token = {
    val request = buildRequest(config.getString("vaultHeader"))
    val creds = getTempCredentials(config)

    val signer = new AWS4Signer()
    signer.setServiceName(request.getServiceName)
    signer.sign(request, creds)

    val headerJson = getSignedHeaders(creds, request)

    val result = vault.auth().loginByAwsIam(role.role, Base64Utils.encodeToString(request.getEndpoint.toString.getBytes()), REQUEST_BODY_BASE64_ENCODED, Base64Utils.encodeToString(headerJson.getBytes()), "aws")
    result.getAuthClientToken
  }

  private def getSignedHeaders(credentials: AWSCredentials, request: DefaultRequest[String]) = {
    val headers = createIamRequestHeaders
    val map = new java.util.LinkedHashMap[String, Any]
    for (entry <- request.getHeaders.entrySet) {
      map.put(entry.getKey, Collections.singletonList(entry.getValue))
    }
    try {
      OBJECT_MAPPER.writeValueAsString(map)
    }
    catch {
      case e: JsonProcessingException => {
        throw new IllegalStateException("Cannot serialize headers to JSON", e)
      }
    }
  }

  private def createIamRequestHeaders: java.util.LinkedHashMap[String, String] = {
    val headers = new java.util.LinkedHashMap[String, String]
    headers.put(HttpHeaders.CONTENT_LENGTH, "" + REQUEST_BODY.length)
    headers.put(HttpHeaders.CONTENT_TYPE, "x-www-form-urlencoded")
    headers
  }

  private def buildRequest(vaultHeader: String): DefaultRequest[String] = {
    val request = new DefaultRequest[String]("sts")

    request.setContent(new ByteArrayInputStream(REQUEST_BODY.getBytes()))
    request.setHttpMethod(HttpMethodName.POST)
    request.setEndpoint(new URI(STS_ENDPOINT))

    request.addHeader(HttpHeaders.CONTENT_TYPE, "" + "application/x-www-form-urlencoded")
    request.addHeader("X-Vault-AWS-IAM-Server-ID", vaultHeader)
    request
  }

  private def getTempCredentials(config: Config) = {

    lazy val STS = AWSSecurityTokenServiceClientBuilder.standard().withRegion(config.getString("region")).build()
    val assumeRole = new AssumeRoleRequest().withRoleArn(s"arn:aws:iam::${config.getString("awsAccountId")}:role/${config.getString("role")}").withRoleSessionName(s"${config.getString("role")}-temp")
    assumeRole.setDurationSeconds(900)
    val credentials = STS.assumeRole(assumeRole).getCredentials
    val sessionCredentials = new BasicSessionCredentials(
      credentials.getAccessKeyId,
      credentials.getSecretAccessKey,
      credentials.getSessionToken)

    new AWSStaticCredentialsProvider(sessionCredentials).getCredentials
  }

/*  private def identity {

    val assumeRoleRequest = new AssumeRoleRequest()
    assumeRoleRequest.setDurationSeconds(900)
    assumeRoleRequest.setRoleSessionName("vault-role-check")

    val req: GetCallerIdentityRequest = new GetCallerIdentityRequest()
    val res: GetCallerIdentityResult = STS.getCallerIdentity(req)
  }*/
}