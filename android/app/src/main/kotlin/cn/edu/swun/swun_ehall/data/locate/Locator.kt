package cn.edu.swun.swun_ehall.data.locate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import cn.edu.swun.swun_ehall.BuildConfig
import cn.edu.swun.swun_ehall.data.model.GeoFix
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class Locator(private val app: Context) {
    private val main = Handler(Looper.getMainLooper())

    suspend fun getFix(): GeoFix = suspendCancellableCoroutine { cont ->
        val key = BuildConfig.AMAP_KEY.trim()
        val fine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (last != null) {
                val src = if (last.provider == LocationManager.GPS_PROVIDER) "gps" else "network"
                cont.resume(
                    GeoFix(last.latitude, last.longitude, last.accuracy.toDouble(), src, "wgs84".takeIf { src == "gps" } ?: "gcj02"),
                )
            } else {
                cont.resumeWithException(IllegalStateException("没有定位权限"))
            }
            return@suspendCancellableCoroutine
        }
        if (key.isEmpty()) {
            cont.resumeWithException(IllegalStateException("未配置高德 key"))
            return@suspendCancellableCoroutine
        }
        try {
            AMapLocationClient.updatePrivacyShow(app, true, true)
            AMapLocationClient.updatePrivacyAgree(app, true)
            AMapLocationClient.setApiKey(key)
            val client = AMapLocationClient(app)
            val opt = AMapLocationClientOption().apply {
                locationPurpose = AMapLocationClientOption.AMapLocationPurpose.SignIn
                isOnceLocation = true
                isOnceLocationLatest = false
                isNeedAddress = false
                isLocationCacheEnable = false
                isWifiScan = true
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            }
            client.setLocationOption(opt)
            client.setLocationListener { loc ->
                client.stopLocation()
                client.onDestroy()
                if (!cont.isActive) return@setLocationListener
                if (loc != null && loc.errorCode == 0 && loc.latitude != 0.0) {
                    cont.resume(
                        GeoFix(loc.latitude, loc.longitude, loc.accuracy.toDouble(), "amap", "gcj02"),
                    )
                } else {
                    cont.resumeWithException(IllegalStateException(loc?.errorInfo ?: "定位失败"))
                }
            }
            main.post { client.startLocation() }
            cont.invokeOnCancellation {
                try {
                    client.stopLocation()
                    client.onDestroy()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }
}
