package com.fyber.consul

import com.ecwid.consul.v1.{ConsulClient, QueryParams}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._

/**
  * Created by dani on 01/02/18.
  */
case class Discovery(private val maybeVaultConfiguration: Option[Config] = None, maybeHost: Option[String] = None, maybeEnvironment: Option[String] = None){
  private val maybeConfig = maybeVaultConfiguration.orElse(Option(ConfigFactory.load().withFallback(ConfigFactory.load("application.conf"))).map(_.getConfig("discovery")))

  private lazy val consul = new ConsulClient(maybeHost.getOrElse(maybeConfig.map(config =>config.getString("host")).getOrElse("localhost")))
  private val maybeDatacenter: Option[String] = maybeEnvironment.orElse(maybeConfig.map(config =>config.getString("environment")))/*todo.getOrElse(getKeyValue("environment"))*/
  private val consulBuilder = QueryParams.Builder.builder()
  private val datacenterParam = maybeDatacenter.map(datacenter => consulBuilder.setDatacenter(datacenter)).getOrElse(consulBuilder).build()

  def getHealthServices(cluster: String, maybeDifferentEnv: Option[String] = None): List[String] =
      consul.getHealthServices(cluster, true, maybeDifferentEnv.map(differentEnv => QueryParams.Builder.builder().setDatacenter(differentEnv).build())
        .getOrElse(datacenterParam)).getValue.toList.map(_.getNode.getAddress)

  def getKvValue(key: String) = new String (consul.getKVBinaryValue(key).getValue.getValue)

  def getMapValues(key: String) = consul.getKVBinaryValues(key).getValue.map(a => (a.getKey, new String(a.getValue))).toMap

  def getListValues(key: String) = consul.getKVBinaryValues(key).getValue.map(a => new String(a.getValue))

}
object Discovery{
  import java.util.Base64

  def base64Decode(encoded: String) = new String(Base64.getDecoder.decode(encoded))

}
