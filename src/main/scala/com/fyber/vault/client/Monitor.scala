package com.fyber.vault.client

import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.{RangeSampler, StartedTimer}
import kamon.prometheus.PrometheusReporter

import scala.concurrent.Future

trait VaultMonitorEngine extends MetricTypes{
}

trait MetricTypes{
  def histogram(name: String, tags: (String, String)*): Unit
}

private[vault] object KamonMonitorEngine  {
  def start(config: Config): VaultMonitorEngine = {

    Kamon.reconfigure(config)
    Kamon.addReporter(new PrometheusReporter())

    new KamonMonitor
  }
}

final class KamonMonitor extends VaultMonitorEngine {

  private final val prefix = "mamba."

  override def histogram(name: String, tags: (String, String)*): Unit = {

    Kamon.histogram(prefix + name).refine(tags: _*).record(1)
  }

}

final class MockMonitor extends VaultMonitorEngine {
  override def histogram(name: String, tags: (String, String)*): Unit = {}
}

/**
  * Created by dani on 13/11/18.
  */
private[vault] class Monitor(monitorEngine: VaultMonitorEngine) {

  def fromPairs(p: (String, String)*): Array[Tag] = p.map(pair => Tag(pair._1, pair._2)).toArray

  /*
    private[client] var client: String = "N/A"
  */
  private def mapTags(tags: Array[Tag]): Map[String, String] = Option(tags).getOrElse(Array.empty).map(_.asKeyValuePair)(collection.breakOut)


  case class Tag(key: String, value: String) {
    def asKeyValuePair = key -> value
  }

  private def recordLease(engine: String, operation: String, result: String) =
    monitorEngine.histogram(s"secrets.engine.$operation.$result", "engine" -> engine /*, ("client", client)*/)

  def recordLeaseRenew(engine: String, result: String) = recordLease(engine, "renew", result)

  def recordLeaseCreate(engine: String, result: String) = recordLease(engine, "create", result)

}
