package cn.edu.swun.swun_ehall.data.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test
    fun leavesCoordinatesOutsideChinaUnchanged() {
        val (lat, lng) = Geo.wgs84ToGcj02(37.39, -122.08)
        assertEquals(37.39, lat, 0.0)
        assertEquals(-122.08, lng, 0.0)
    }

    @Test
    fun chengduWgsShiftsHundredsOfMetersToGcj() {
        val lat = 30.6370
        val lng = 104.0430
        val gcj = Geo.wgs84ToGcj02(lat, lng)
        val d = Geo.meters(lat, lng, gcj.first, gcj.second)
        assertTrue(d > 300)
        assertTrue(d < 700)
        assertTrue(gcj.first != lat)
        assertTrue(gcj.second != lng)
    }

    @Test
    fun campusGcjConvertsGpsAndFusedButNotAmapOrNetwork() {
        val lat = 30.56829
        val lng = 103.96972
        val wgs = Geo.gcj02ToWgs84(lat, lng)
        val gps = Geo.campusGcj02(wgs.first, wgs.second, "gps")
        val fused = Geo.campusGcj02(wgs.first, wgs.second, "fused")
        val amap = Geo.campusGcj02(lat, lng, "amap")
        val net = Geo.campusGcj02(wgs.first, wgs.second, "network")
        assertTrue(Geo.meters(lat, lng, gps.first, gps.second) < 1)
        assertTrue(Geo.meters(lat, lng, fused.first, fused.second) < 1)
        assertEquals(lat, amap.first, 0.0)
        assertEquals(lng, amap.second, 0.0)
        assertEquals(wgs.first, net.first, 0.0)
        assertEquals(wgs.second, net.second, 0.0)
        val already = Geo.campusGcj02(lat, lng, "gps", datum = "gcj02")
        assertEquals(lat, already.first, 0.0)
        assertEquals(lng, already.second, 0.0)
    }

    @Test
    fun geoIsWgs84TreatsFusedAliasesAsWgs() {
        assertTrue(Geo.isWgs84("fused"))
        assertTrue(Geo.isWgs84("gps", provider = "fused"))
        assertFalse(Geo.isWgs84("amap"))
        assertFalse(Geo.isWgs84("network"))
        assertFalse(Geo.isWgs84("gps", datum = "gcj02"))
    }
}
