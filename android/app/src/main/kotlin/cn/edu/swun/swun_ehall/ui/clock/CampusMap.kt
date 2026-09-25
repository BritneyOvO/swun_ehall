package cn.edu.swun.swun_ehall.ui.clock

import android.graphics.Color as AndroidColor
import android.graphics.Outline
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.swun.swun_ehall.data.geo.Geo
import cn.edu.swun.swun_ehall.data.model.ClockFence
import cn.edu.swun.swun_ehall.data.model.GeoFix
import cn.edu.swun.swun_ehall.ui.theme.ThemeMode
import cn.edu.swun.swun_ehall.ui.theme.ThemeSettings
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolygonOptions

@Composable
fun CampusMap(
    me: GeoFix?,
    fences: List<ClockFence>,
    modifier: Modifier = Modifier,
    height: Dp = 320.dp,
    zoom: Float? = null,
) {
    val context = LocalContext.current
    val theme: ThemeSettings = viewModel()
    val dark = when (theme.mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val mapView = remember {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        CampusMapView(context).apply { onCreate(Bundle()) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDestroy()
        }
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)),
        update = { view ->
            val fenceKey = "$zoom|" + fences.joinToString(";") { "${it.name}|${it.latitude}|${it.longitude}|${it.radiusMeters}" }
            val locKey = "${me?.latitude}|${me?.longitude}"
            if (view.drawnFence == fenceKey && view.drawnLoc == locKey && view.drawnDark == dark) return@AndroidView
            val map = view.map ?: return@AndroidView
            val refit = view.drawnFence != fenceKey || view.drawnDark != dark
            view.drawnFence = fenceKey
            view.drawnLoc = locKey
            view.drawnDark = dark
            drawCampus(map, me, fences, dark, refit, zoom)
        },
    )
}

private fun drawCampus(map: AMap, me: GeoFix?, fences: List<ClockFence>, dark: Boolean, refit: Boolean, zoom: Float?) {
    map.mapType = if (dark) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
    map.uiSettings.isZoomControlsEnabled = false
    map.uiSettings.isMyLocationButtonEnabled = false
    map.uiSettings.setAllGesturesEnabled(false)
    map.clear()
    fences.forEach { fence ->
        val ring = fence.area()
        if (ring.size >= 3) {
            map.addPolygon(
                PolygonOptions()
                    .addAll(ring)
                    .fillColor(0x662BB5A0)
                    .strokeColor(0xFF2BB5A0.toInt())
                    .strokeWidth(4f),
            )
        } else if (fence.radiusMeters > 0.0) {
            map.addCircle(
                CircleOptions()
                    .center(LatLng(fence.latitude, fence.longitude))
                    .radius(fence.radiusMeters)
                    .fillColor(0x662BB5A0)
                    .strokeColor(0xFF2BB5A0.toInt())
                    .strokeWidth(4f),
            )
        }
        val center = fence.center()
        map.addMarker(
            MarkerOptions()
                .position(center)
                .title(fence.name.ifBlank { "中心" })
                .snippet("%.6f, %.6f".format(center.latitude, center.longitude)),
        )
    }
    val style = MyLocationStyle()
    style.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
    style.strokeColor(AndroidColor.WHITE)
    style.radiusFillColor(0x332E7DFF)
    style.interval(4000)
    map.myLocationStyle = style
    map.isMyLocationEnabled = true
    if (me != null) {
        map.addMarker(
            MarkerOptions()
                .position(LatLng(me.latitude, me.longitude))
                .title("我的位置")
                .snippet(me.coordText),
        )
    }
    if (refit) frameCampus(map, fences, me, zoom)
}

private fun frameCampus(map: AMap, fences: List<ClockFence>, me: GeoFix?, zoom: Float?) {
    if (zoom != null) {
        val center = fences.firstOrNull()?.center()
            ?: me?.let { LatLng(it.latitude, it.longitude) }
            ?: return
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
        return
    }
    if (fences.isEmpty()) {
        if (me != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(me.latitude, me.longitude), 16f))
        }
        return
    }
    val bounds = LatLngBounds.Builder()
    fences.forEach { fence ->
        val ring = fence.area()
        if (ring.isNotEmpty()) {
            ring.forEach { bounds.include(it) }
        } else {
            val radius = fence.radiusMeters.coerceAtLeast(80.0)
            val dLat = radius / 111_320.0
            val cos = kotlin.math.cos(Math.toRadians(fence.latitude)).coerceAtLeast(0.2)
            val dLng = radius / (111_320.0 * cos)
            bounds.include(LatLng(fence.latitude - dLat, fence.longitude - dLng))
            bounds.include(LatLng(fence.latitude + dLat, fence.longitude + dLng))
        }
    }
    if (me != null && fences.any { it.covers(me) }) {
        bounds.include(LatLng(me.latitude, me.longitude))
    }
    try {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 48))
    } catch (_: Exception) {
        val first = fences.first()
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 14f))
    }
}

private fun ClockFence.covers(me: GeoFix): Boolean {
    val limit = if (radiusMeters > 0.0) radiusMeters * 3 else 2_000.0
    return Geo.meters(me.latitude, me.longitude, latitude, longitude) < limit
}

private fun ClockFence.center(): LatLng {
    val ring = area()
    if (ring.isEmpty()) return LatLng(latitude, longitude)
    return LatLng(ring.map { it.latitude }.average(), ring.map { it.longitude }.average())
}

private fun ClockFence.area(): List<LatLng> {
    if (polygon.size >= 3) return polygon.map { LatLng(it.first, it.second) }
    if (radiusMeters <= 0.0) return emptyList()
    val steps = 48
    val lat = latitude
    val cos = kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.2)
    return List(steps) { i ->
        val t = 2.0 * Math.PI * i / steps
        val dLat = radiusMeters * kotlin.math.cos(t) / 111_320.0
        val dLng = radiusMeters * kotlin.math.sin(t) / (111_320.0 * cos)
        LatLng(lat + dLat, longitude + dLng)
    }
}

/** Texture map so the rounded corner can clip. Touch handling is the map SDK default. */
private class CampusMapView(context: android.content.Context) : TextureMapView(context) {
    var drawnFence: String? = null
    var drawnLoc: String? = null
    var drawnDark: Boolean? = null

    init {
        clipToOutline = true
        outlineProvider = roundOutline
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        clipRound(this)
    }

    private fun clipRound(view: View) {
        view.outlineProvider = roundOutline
        view.clipToOutline = true
        if (view is TextureView) view.isOpaque = false
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) clipRound(view.getChildAt(i))
        }
    }

    private companion object {
        val roundOutline = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = 16f * view.resources.displayMetrics.density
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
    }
}
