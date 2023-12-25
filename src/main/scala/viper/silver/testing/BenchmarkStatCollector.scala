package viper.silver.testing

import scala.collection.mutable


object BenchmarkStatCollector {
  private val _stats = new mutable.HashMap[String, Int]()

  private var isReady = false

  def initTest() = {
    BenchmarkStatCollector.synchronized{
      assert(!isReady)
      isReady = true
    }
  }

  private def getStats() = {
    assert(isReady)
    _stats
  }

  def addStat(stat: String) = {
    BenchmarkStatCollector.synchronized{
      getStats().update(stat, 0)
    }
  }

  def addToStat(stat: String, value: Int) = {
    val map = getStats()
    val current = map(stat)
    map.update(stat, current + value)
  }

  def endTest() = {
    BenchmarkStatCollector.synchronized{
      assert(isReady)
      isReady = false
      val res = _stats.toMap
      _stats.clear()
      res
    }
  }
}