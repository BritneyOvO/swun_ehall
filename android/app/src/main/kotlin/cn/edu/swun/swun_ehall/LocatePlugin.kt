package cn.edu.swun.swun_ehall

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocatePlugin(private val app: Context) : MethodChannel.MethodCallHandler {
    private val main = Handler(Looper.getMainLooper())
    private val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var amapKey: String = BuildConfig.AMAP_KEY.trim()
    private var amap: AMapLocationClient? = null
    private var amapDisabled = false
    private var privacyReady = false

    companion object {
        const val CHANNEL = "cn.edu.swun.swun_ehall/locate"
        private const val TAG = "SwunLocate"

        fun registerWith(engine: FlutterEngine, ctx: Context) {
            val plugin = LocatePlugin(ctx.applicationContext)
            MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler(plugin)
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "setKey" -> {
                val key = (call.arguments as? String)?.trim().orEmpty()
                if (key.isNotEmpty()) {
                    amapKey = key
                    amapDisabled = false
                    destroyAmap()
                }
                result.success(amapKey.isNotEmpty())
            }
            "info" -> result.success(info())
            "warmup" -> {
                warmup()
                result.success(true)
            }
            "getFix" -> {
                val timeout = ((call.argument<Number>("timeoutMs")?.toLong()) ?: 8000L).coerceIn(1500L, 20000L)
                val force = call.argument<Boolean>("force") == true
                getFix(timeout, force, result)
            }
            else -> result.notImplemented()
        }
    }

    private fun info(): HashMap<String, Any> {
        return hashMapOf<String, Any>(
            "package" to app.packageName,
            "sha1" to signingSha1(),
            "hasKey" to amapKey.isNotEmpty(),
            "amap" to (!amapDisabled && amapKey.isNotEmpty()),
            "sdk" to "amap-location 6.5.1",
        )
    }

    private fun warmup() {
        if (!hasLocationPermission()) return
        tryAmapLast(maxAgeMs = 20_000L)?.let { return }
        startAmap(continuous = false) { }
        val last = systemLast()
        if (last != null) return
        requestSystem(object : LocationListener {
            override fun onLocationChanged(location: Location) {
                stopSystem(this)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }, 4000L)
    }

    private fun getFix(timeoutMs: Long, force: Boolean, result: MethodChannel.Result) {
        if (!hasLocationPermission()) {
            result.error("PERMISSION", "需要定位权限", null)
            return
        }
        val session = FixSession(timeoutMs, force, result)
        session.start()
    }

    private inner class FixSession(
        private val timeoutMs: Long,
        private val force: Boolean,
        private val result: MethodChannel.Result,
    ) {
        private var done = false
        private var best: HashMap<String, Any>? = null
        private var systemListener: LocationListener? = null
        private var amapClient: AMapLocationClient? = null
        private var amapHeard = false
        private val samples = ArrayList<HashMap<String, Any>>()

        fun start() {
            if (!force) {
                val lastAmap = tryAmapLast(maxAgeMs = 6_000L)
                if (lastAmap != null) offer(lastAmap)
            }
            startAmap(continuous = force) { loc ->
                if (loc.errorCode == 0 && loc.latitude != 0.0) {
                    amapHeard = true
                    offer(packAmap(loc))
                } else if (loc.errorCode == 7 || loc.errorCode == 11) {
                    amapDisabled = true
                    Log.w(TAG, "amap auth ${loc.errorCode} ${loc.errorInfo}")
                }
            }.also { amapClient = it }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    offer(pack(location, sourceOf(location), location.time))
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            systemListener = listener
            requestSystem(listener, timeoutMs)
            val waitAmap = !amapDisabled && amapKey.isNotEmpty()
            main.postDelayed({
                if (done) return@postDelayed
                if (force && waitAmap && amapCount() < 2) return@postDelayed
                pickStable()?.let { finish(it) }
            }, if (force) 4500L else 2200L)
            main.postDelayed({ finish(pickStable()) }, timeoutMs)
        }

        private fun offer(row: HashMap<String, Any>) {
            main.post {
                if (done) return@post
                samples.add(row)
                val prev = best
                if (prev == null || score(row) < score(prev)) {
                    best = row
                }
                if (force) {
                    if (amapCount() >= 2 && clusterTight() && score(row) <= 40.0) {
                        finish(pickStable())
                    }
                    return@post
                }
                val src = row["source"] as String
                val acc = row["accuracy"] as Double
                if (src == "amap" && acc in 0.1..35.0 && ageMs(row) <= 6_000) {
                    finish(row)
                    return@post
                }
                if (acc in 0.1..25.0 && ageMs(row) <= 5_000 && src != "last" && src != "gps") {
                    finish(row)
                }
            }
        }

        private fun amapCount(): Int = samples.count { it["source"] == "amap" }

        private fun clusterTight(): Boolean {
            val amap = samples.filter { it["source"] == "amap" }
            if (amap.size < 2) return false
            val last = amap.takeLast(3)
            for (i in last.indices) {
                for (j in i + 1 until last.size) {
                    if (meters(last[i], last[j]) > 40.0) return false
                }
            }
            return true
        }

        private fun pickStable(): HashMap<String, Any>? {
            val fresh = samples.filter { ageMs(it) <= 10_000 }
            val amap = fresh.filter { it["source"] == "amap" }
            val pool = when {
                amap.size >= 2 -> amap
                fresh.isNotEmpty() -> fresh
                else -> samples
            }
            if (pool.isEmpty()) return best
            var winner = pool.last()
            var bestSum = Double.POSITIVE_INFINITY
            for (p in pool) {
                var sum = 0.0
                for (q in pool) sum += meters(p, q)
                sum += score(p)
                if (sum < bestSum) {
                    bestSum = sum
                    winner = p
                }
            }
            return winner
        }

        /** 越小越好。室内 GPS 精度数字常虚报，高德 Wi‑Fi 更接近校方围栏。 */
        private fun score(row: HashMap<String, Any>): Double {
            val acc = (row["accuracy"] as Double).let { if (it > 0) it else 80.0 }
            val src = row["source"] as String
            val age = ageMs(row)
            var s = acc
            when (src) {
                "amap" -> s -= 12.0
                "network" -> s += 8.0
                "gps" -> s += 32.0
                "last" -> s += 55.0
            }
            if (age > 4_000) s += (age - 4_000) / 250.0
            return s
        }

        private fun finish(row: HashMap<String, Any>?) {
            if (done) return
            done = true
            systemListener?.let { stopSystem(it) }
            systemListener = null
            try {
                amapClient?.stopLocation()
            } catch (_: Exception) {
            }
            val out = row ?: pickStable() ?: best
            main.post {
                if (out != null) result.success(out)
                else result.error("NO_FIX", "无法获取定位", null)
            }
        }
    }

    private fun startAmap(continuous: Boolean, onLoc: (AMapLocation) -> Unit): AMapLocationClient? {
        if (amapDisabled || amapKey.isEmpty() || !hasLocationPermission()) return null
        return try {
            ensurePrivacy()
            AMapLocationClient.setApiKey(amapKey)
            val client = AMapLocationClient(app)
            val opt = AMapLocationClientOption().apply {
                if (continuous) {
                    isOnceLocation = false
                    interval = 800
                    httpTimeOut = 8000
                } else {
                    locationPurpose = AMapLocationClientOption.AMapLocationPurpose.SignIn
                    isOnceLocation = true
                    isOnceLocationLatest = false
                    httpTimeOut = 5000
                }
                isNeedAddress = false
                isLocationCacheEnable = false
                isWifiScan = true
                isMockEnable = false
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            }
            client.setLocationOption(opt)
            client.setLocationListener { loc ->
                if (loc != null) onLoc(loc)
            }
            client.startLocation()
            amap = client
            client
        } catch (e: Exception) {
            Log.w(TAG, "amap start $e")
            amapDisabled = true
            null
        }
    }

    private fun tryAmapLast(maxAgeMs: Long = 20_000L): HashMap<String, Any>? {
        if (amapDisabled || amapKey.isEmpty()) return null
        return try {
            ensurePrivacy()
            AMapLocationClient.setApiKey(amapKey)
            val client = amap ?: AMapLocationClient(app).also { amap = it }
            val last = client.lastKnownLocation ?: return null
            if (last.errorCode != 0 || last.latitude == 0.0) return null
            if (ageMs(last.time) > maxAgeMs) return null
            packAmap(last)
        } catch (_: Exception) {
            null
        }
    }

    private fun ensurePrivacy() {
        if (privacyReady) return
        AMapLocationClient.updatePrivacyShow(app, true, true)
        AMapLocationClient.updatePrivacyAgree(app, true)
        privacyReady = true
    }

    private fun destroyAmap() {
        try {
            amap?.onDestroy()
        } catch (_: Exception) {
        }
        amap = null
    }

    private fun requestSystem(listener: LocationListener, timeoutMs: Long) {
        val looper = Looper.getMainLooper()
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, looper)
            }
        } catch (e: Exception) {
            Log.w(TAG, "network $e")
        }
        try {
            if (hasFine() && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, looper)
            }
        } catch (e: Exception) {
            Log.w(TAG, "gps $e")
        }
        main.postDelayed({ stopSystem(listener) }, timeoutMs + 200)
    }

    private fun stopSystem(listener: LocationListener) {
        try {
            lm.removeUpdates(listener)
        } catch (_: Exception) {
        }
    }

    private fun systemLast(): Location? {
        val names = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var best: Location? = null
        for (name in names) {
            val loc = try {
                lm.getLastKnownLocation(name)
            } catch (_: Exception) {
                null
            } ?: continue
            if (best == null || loc.accuracy < best.accuracy || (best.accuracy <= 0 && loc.accuracy > 0)) {
                best = loc
            }
        }
        return best
    }

    private fun packAmap(loc: AMapLocation): HashMap<String, Any> {
        val type = loc.locationType
        val source = when (type) {
            AMapLocation.LOCATION_TYPE_GPS -> "amap"
            AMapLocation.LOCATION_TYPE_WIFI,
            AMapLocation.LOCATION_TYPE_CELL,
            AMapLocation.LOCATION_TYPE_AMAP,
            AMapLocation.LOCATION_TYPE_NETWORK,
            AMapLocation.LOCATION_TYPE_COARSE_LOCATION -> "amap"
            AMapLocation.LOCATION_TYPE_FIX_CACHE,
            AMapLocation.LOCATION_TYPE_LAST_LOCATION_CACHE,
            AMapLocation.LOCATION_TYPE_FAST -> "last"
            else -> "amap"
        }
        return pack(loc.latitude, loc.longitude, loc.accuracy.toDouble(), source, loc.time)
    }

    private fun pack(loc: Location, source: String, time: Long): HashMap<String, Any> {
        var lat = loc.latitude
        var lng = loc.longitude
        // 系统 GPS 是 WGS-84；校方围栏是 GCJ-02。网络定位在国产 ROM 上已经是 GCJ-02。
        if (loc.provider == LocationManager.GPS_PROVIDER) {
            val gcj = wgs84ToGcj02(lat, lng)
            lat = gcj.first
            lng = gcj.second
        }
        return pack(lat, lng, loc.accuracy.toDouble(), source, time)
    }

    private fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        if (lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271) {
            return Pair(lat, lng)
        }
        val a = 6378245.0
        val ee = 0.00669342162296594323
        val dLat = transformLat(lng - 105.0, lat - 35.0)
        val dLng = transformLng(lng - 105.0, lat - 35.0)
        val rad = lat / 180.0 * PI
        var magic = sin(rad)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        val nlat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * PI)
        val nlng = (dLng * 180.0) / (a / sqrtMagic * cos(rad) * PI)
        return Pair(lat + nlat, lng + nlng)
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

    private fun meters(a: HashMap<String, Any>, b: HashMap<String, Any>): Double {
        val lat1 = Math.toRadians(a["latitude"] as Double)
        val lat2 = Math.toRadians(b["latitude"] as Double)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians((b["longitude"] as Double) - (a["longitude"] as Double))
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 6371000.0 * 2 * kotlin.math.atan2(sqrt(h), sqrt(1 - h))
    }

    private fun pack(lat: Double, lng: Double, acc: Double, source: String, time: Long): HashMap<String, Any> {
        return hashMapOf(
            "latitude" to lat,
            "longitude" to lng,
            "accuracy" to if (acc.isFinite()) acc else 0.0,
            "source" to source,
            "time" to time,
        )
    }

    private fun sourceOf(loc: Location): String {
        return when (loc.provider) {
            LocationManager.NETWORK_PROVIDER -> "network"
            LocationManager.GPS_PROVIDER -> "gps"
            else -> "last"
        }
    }

    private fun ageMs(row: HashMap<String, Any>): Long {
        val t = (row["time"] as? Number)?.toLong() ?: return 0L
        return ageMs(t)
    }

    private fun ageMs(time: Long): Long {
        if (time <= 0L) return 0L
        val now = System.currentTimeMillis()
        return (now - time).coerceAtLeast(0L)
    }

    private fun hasLocationPermission(): Boolean = hasFine() || hasCoarse()

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasCoarse(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun signingSha1(): String {
        return try {
            val bytes: ByteArray? = if (Build.VERSION.SDK_INT >= 28) {
                val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            }
            if (bytes == null) return ""
            val md = MessageDigest.getInstance("SHA1").digest(bytes)
            md.joinToString(":") { b -> "%02X".format(b) }
        } catch (_: Exception) {
            ""
        }
    }
}
