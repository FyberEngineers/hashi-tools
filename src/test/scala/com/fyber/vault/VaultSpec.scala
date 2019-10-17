package com.fyber.vault

/**
  * Created by dani on 02/09/18.
  */

import com.fyber.vault.client.Secrets
import com.fyber.vault.engines.KV.{Key, Path}
import com.typesafe.config.ConfigFactory
//import org.junit.runner.RunWith
import org.scalatest.{FunSpecLike, Matchers}

//@RunWith(classOf[JUnitRunner])
class VaultSpec extends FunSpecLike with Matchers {
  def config = ConfigFactory.load("reference.conf").getConfig("secrets")


  describe("Test metrics data") {

    ignore("should check creation aws keys") {
      val secrets = Secrets(config)
      val credentials = secrets.engines.kv.getCredentials(Path("my-secret"), Key("my-value"))

      println(credentials)
    }

  }
}
