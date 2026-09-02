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
                val timeout = ((call.argument<Number>("timeoutMs")?.toLong()) ?: 8000L).coerceIn(1500L, 15000L)
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
        tryAmapLast()?.let { return }
        startAmapOnce { }
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

        fun start() {
            val lastAmap = tryAmapLast()
            if (lastAmap != null) offer(lastAmap)
            val lastSys = systemLast()
            if (lastSys != null) offer(pack(lastSys, sourceOf(lastSys), lastSys.time))
            if (!force && best != null && (best!!["accuracy"] as Double) <= 60.0 && ageMs(best!!) <= 20_000) {
                finish(best)
                return
            }
            startAmapOnce { loc ->
                if (loc.errorCode == 0 && loc.latitude != 0.0) {
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
            main.postDelayed({ if (!done && best != null && (best!!["accuracy"] as Double) <= 150.0) finish(best) }, 2200L)
            main.postDelayed({ finish(best) }, timeoutMs)
        }

        private fun offer(row: HashMap<String, Any>) {
            main.post {
                if (done) return@post
                val acc = row["accuracy"] as Double
                val prev = best
                val prevAcc = (prev?.get("accuracy") as? Double) ?: Double.POSITIVE_INFINITY
                if (prev == null || (acc > 0 && acc < prevAcc)) {
                    best = row
                }
                if (acc in 0.1..55.0) finish(row)
            }
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
            main.post {
                if (row != null) result.success(row)
                else result.error("NO_FIX", "无法获取定位", null)
            }
        }
    }

    private fun startAmapOnce(onLoc: (AMapLocation) -> Unit): AMapLocationClient? {
        if (amapDisabled || amapKey.isEmpty() || !hasLocationPermission()) return null
        return try {
            ensurePrivacy()
            AMapLocationClient.setApiKey(amapKey)
            val client = AMapLocationClient(app)
            val opt = AMapLocationClientOption().apply {
                locationPurpose = AMapLocationClientOption.AMapLocationPurpose.SignIn
                isOnceLocation = true
                isOnceLocationLatest = true
                httpTimeOut = 5000
                isNeedAddress = false
                isLocationCacheEnable = true
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

    private fun tryAmapLast(): HashMap<String, Any>? {
        if (amapDisabled || amapKey.isEmpty()) return null
        return try {
            ensurePrivacy()
            AMapLocationClient.setApiKey(amapKey)
            val client = amap ?: AMapLocationClient(app).also { amap = it }
            val last = client.lastKnownLocation ?: return null
            if (last.errorCode != 0 || last.latitude == 0.0) return null
            if (ageMs(last.time) > 90_000) return null
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
            AMapLocation.LOCATION_TYPE_GPS -> "gps"
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
        return pack(loc.latitude, loc.longitude, loc.accuracy.toDouble(), source, time)
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
