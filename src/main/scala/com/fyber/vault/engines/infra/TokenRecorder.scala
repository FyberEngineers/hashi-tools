package com.fyber.vault.engines.infra

import com.bettercloud.vault.VaultException
import com.bettercloud.vault.response.VaultResponse
import com.fyber.vault.client.{Monitor, VaultMonitorEngine}

import scala.util.{Failure, Success, Try}

/**
  * Created by dani on 10/03/19.
  */
trait TokenRecorder {
  this: SecretEntity =>

  protected val monitorEngine: VaultMonitorEngine
  implicit val monitor = new Monitor(monitorEngine)

  protected def recordLeaseRenewSucceed() = monitor.recordLeaseRenew(name, "succeed")

  protected def recordLeaseRenewFailed() = monitor.recordLeaseRenew(name, "failed")

  protected def recordLeaseCreateSucceed() = monitor.recordLeaseCreate(name, "succeed")

  protected def recordLeaseCreateFailed() = monitor.recordLeaseCreate(name, "failed")


  def tryToken[R <: VaultResponse](tryResponse: Try[R], successMetric: () => Unit, failedMetric: () => Unit): R = {
    tryResponse match {
      case Success(r) => successMetric(); r
      case Failure(e) =>
        e match {
          case v: VaultException =>
            //todo: log error, indicates vault exception
            failedMetric()
            throw v
          case e: Exception =>
            //todo: log error
            failedMetric()
            throw e
        }
    }
  }
}