package cn.edu.swun.swun_ehall.data.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Campus check-in fences are GCJ-02. GPS/fused chips emit WGS-84.
 * Amap and CN-ROM network are already GCJ-02.
 */
object Geo {
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val EARTH = 6371000.0

    fun outOfChina(lat: Double, lng: Double): Boolean {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    fun isWgs84(source: String, datum: String? = null, provider: String? = null): Boolean {
        val d = datum?.trim()?.lowercase()
        if (d == "gcj02") return false
        if (d == "wgs84") return true
        val p = provider?.trim()?.lowercase().orEmpty()
        if (p == "network") return false
        if (p == "gps" ||
            p == "passive" ||
            p.contains("fused") ||
            (p.contains("gps") && !p.contains("network"))
        ) {
            return true
        }
        return when (source) {
            "amap", "network", "demo" -> false
            "gps", "fused" -> true
            else -> false
        }
    }

    fun campusGcj02(
        lat: Double,
        lng: Double,
        source: String,
        datum: String? = null,
        provider: String? = null,
    ): Pair<Double, Double> {
        if (!isWgs84(source, datum, provider)) return lat to lng
        return wgs84ToGcj02(lat, lng)
    }

    fun gcj02ToWgs84(lat: Double, lng: Double): Pair<Double, Double> {
        if (!lat.isFinite() || !lng.isFinite()) return lat to lng
        if (outOfChina(lat, lng)) return lat to lng
        var wgsLat = lat
        var wgsLng = lng
        repeat(8) {
            val g = wgs84ToGcj02(wgsLat, wgsLng)
            wgsLat -= g.first - lat
            wgsLng -= g.second - lng
        }
        return wgsLat to wgsLng
    }

    fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        if (!lat.isFinite() || !lng.isFinite()) return lat to lng
        if (outOfChina(lat, lng)) return lat to lng
        val dLat = transformLat(lng - 105.0, lat - 35.0)
        val dLng = transformLng(lng - 105.0, lat - 35.0)
        val rad = lat / 180.0 * PI
        var magic = sin(rad)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val nlat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val nlng = (dLng * 180.0) / (A / sqrtMagic * cos(rad) * PI)
        return (lat + nlat) to (lng + nlng)
    }

    fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = lat1 * PI / 180
        val p2 = lat2 * PI / 180
        val dP = (lat2 - lat1) * PI / 180
        val dL = (lng2 - lng1) * PI / 180
        val a = sin(dP / 2) * sin(dP / 2) + cos(p1) * cos(p2) * sin(dL / 2) * sin(dL / 2)
        return 2 * EARTH * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
