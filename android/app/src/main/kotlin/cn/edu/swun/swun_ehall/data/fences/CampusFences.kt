package cn.edu.swun.swun_ehall.data.fences

import kotlin.random.Random

data class CampusFence(
    val name: String,
    val southLat: Double,
    val southLng: Double,
    val northLat: Double,
    val northLng: Double,
    val westLat: Double,
    val westLng: Double,
    val eastLat: Double,
    val eastLng: Double,
    val centerLat: Double,
    val centerLng: Double,
) {
    fun contains(lat: Double, lng: Double): Boolean {
        val poly = arrayOf(
            southLat to southLng,
            eastLat to eastLng,
            northLat to northLng,
            westLat to westLng,
        )
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val yi = poly[i].first
            val xi = poly[i].second
            val yj = poly[j].first
            val xj = poly[j].second
            val cross = (yi > lat) != (yj > lat) &&
                lng < (xj - xi) * (lat - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-12) + xi
            if (cross) inside = !inside
            j = i
        }
        return inside
    }

    fun randomInside(random: Random = Random): Pair<Double, Double> {
        val lats = doubleArrayOf(southLat, northLat, westLat, eastLat)
        val lngs = doubleArrayOf(southLng, northLng, westLng, eastLng)
        val minLat = lats.min()
        val maxLat = lats.max()
        val minLng = lngs.min()
        val maxLng = lngs.max()
        repeat(48) {
            val lat = minLat + random.nextDouble() * (maxLat - minLat)
            val lng = minLng + random.nextDouble() * (maxLng - minLng)
            if (!contains(lat, lng)) return@repeat
            return centerLat + (lat - centerLat) * 0.85 to centerLng + (lng - centerLng) * 0.85
        }
        return centerLat to centerLng
    }
}

object CampusFences {
    val bs = CampusFence(
        name = "BS",
        southLat = 30.56059, southLng = 103.97158,
        northLat = 30.57513, northLng = 103.97098,
        westLat = 30.56781, westLng = 103.96942,
        eastLat = 30.56800, eastLng = 103.97278,
        centerLat = 30.56817, centerLng = 103.97111,
    )
    val bw = CampusFence(
        name = "BW",
        southLat = 30.56172, southLng = 103.96863,
        northLat = 30.57547, northLng = 103.96975,
        westLat = 30.56766, westLng = 103.96774,
        eastLat = 30.56796, eastLng = 103.97118,
        centerLat = 30.56829, centerLng = 103.96972,
    )
    val bx = CampusFence(
        name = "BX",
        southLat = 30.56152, southLng = 103.96960,
        northLat = 30.57485, northLng = 103.96960,
        westLat = 30.56819, westLng = 103.96785,
        eastLat = 30.56819, eastLng = 103.97135,
        centerLat = 30.56819, centerLng = 103.96960,
    )
    val h = CampusFence(
        name = "H",
        southLat = 30.55718, southLng = 103.96894,
        northLat = 30.57137, northLng = 103.96815,
        westLat = 30.56444, westLng = 103.96702,
        eastLat = 30.56001, eastLng = 103.97020,
        centerLat = 30.56428, centerLng = 103.96861,
    )

    val all = listOf(bs, bw, bx, h)

    /** 70 m grid from checkAllowSign. Left edge south to north, then right edge back. */
    val outlines: Map<String, List<Pair<Double, Double>>> = mapOf(
        "H" to listOf(
            30.557773 to 103.968572,
            30.558402 to 103.967842,
            30.559031 to 103.967842,
            30.559659 to 103.967842,
            30.560288 to 103.967842,
            30.560917 to 103.967842,
            30.561546 to 103.967842,
            30.562175 to 103.967112,
            30.562803 to 103.967112,
            30.563432 to 103.967112,
            30.564061 to 103.967112,
            30.564690 to 103.967112,
            30.565319 to 103.967112,
            30.565948 to 103.967112,
            30.566576 to 103.967112,
            30.567205 to 103.967112,
            30.567834 to 103.967842,
            30.568463 to 103.967843,
            30.569092 to 103.967843,
            30.569720 to 103.967843,
            30.570349 to 103.967843,
            30.570978 to 103.968573,
            30.571607 to 103.968573,
            30.571607 to 103.969303,
            30.570978 to 103.969303,
            30.570349 to 103.969303,
            30.569720 to 103.970034,
            30.569092 to 103.970034,
            30.568463 to 103.970033,
            30.567834 to 103.970033,
            30.567205 to 103.970033,
            30.566576 to 103.970033,
            30.565948 to 103.970033,
            30.565319 to 103.970033,
            30.564690 to 103.970033,
            30.564061 to 103.970033,
            30.563432 to 103.970033,
            30.562803 to 103.970033,
            30.562175 to 103.970033,
            30.561546 to 103.970033,
            30.560917 to 103.970033,
            30.560288 to 103.970033,
            30.559659 to 103.970033,
            30.559031 to 103.969302,
            30.558402 to 103.969302,
            30.557773 to 103.969302,
        ),
        "BS" to listOf(
            30.560917 to 103.970763,
            30.561546 to 103.970763,
            30.562175 to 103.970033,
            30.562803 to 103.970033,
            30.563432 to 103.970033,
            30.564061 to 103.970033,
            30.564690 to 103.970033,
            30.565319 to 103.970033,
            30.565948 to 103.970033,
            30.566576 to 103.970033,
            30.567205 to 103.970033,
            30.567834 to 103.970033,
            30.568463 to 103.970033,
            30.569092 to 103.970034,
            30.569720 to 103.970034,
            30.570349 to 103.970034,
            30.570978 to 103.970034,
            30.571607 to 103.970034,
            30.572236 to 103.970034,
            30.572865 to 103.970034,
            30.573493 to 103.970034,
            30.574122 to 103.970764,
            30.574751 to 103.970764,
            30.574751 to 103.971495,
            30.574122 to 103.971495,
            30.573493 to 103.972225,
            30.572865 to 103.972225,
            30.572236 to 103.972225,
            30.571607 to 103.972225,
            30.570978 to 103.972225,
            30.570349 to 103.972225,
            30.569720 to 103.972225,
            30.569092 to 103.972224,
            30.568463 to 103.972955,
            30.567834 to 103.972955,
            30.567205 to 103.972955,
            30.566576 to 103.972224,
            30.565948 to 103.972224,
            30.565319 to 103.972224,
            30.564690 to 103.972224,
            30.564061 to 103.972224,
            30.563432 to 103.972224,
            30.562803 to 103.972224,
            30.562175 to 103.972224,
            30.561546 to 103.971493,
            30.560917 to 103.971493,
        ),
        "BW" to listOf(
            30.560917 to 103.969303,
            30.561546 to 103.969303,
            30.562175 to 103.968572,
            30.562803 to 103.968572,
            30.563432 to 103.968572,
            30.564061 to 103.968573,
            30.564690 to 103.967842,
            30.565319 to 103.967842,
            30.565948 to 103.967842,
            30.566576 to 103.967842,
            30.567205 to 103.967842,
            30.567834 to 103.967842,
            30.568463 to 103.967843,
            30.569092 to 103.967843,
            30.569720 to 103.967843,
            30.570349 to 103.967843,
            30.570978 to 103.967843,
            30.571607 to 103.967843,
            30.572236 to 103.968573,
            30.572865 to 103.968573,
            30.573493 to 103.968573,
            30.574122 to 103.968573,
            30.574751 to 103.969304,
            30.575380 to 103.969304,
            30.575380 to 103.969304,
            30.574751 to 103.970034,
            30.574122 to 103.970034,
            30.573493 to 103.970034,
            30.572865 to 103.970764,
            30.572236 to 103.970764,
            30.571607 to 103.970764,
            30.570978 to 103.970764,
            30.570349 to 103.970764,
            30.569720 to 103.970764,
            30.569092 to 103.970764,
            30.568463 to 103.970764,
            30.567834 to 103.970764,
            30.567205 to 103.970764,
            30.566576 to 103.970764,
            30.565948 to 103.970764,
            30.565319 to 103.970764,
            30.564690 to 103.970763,
            30.564061 to 103.970763,
            30.563432 to 103.970763,
            30.562803 to 103.970033,
            30.562175 to 103.970033,
            30.561546 to 103.970033,
            30.560917 to 103.969303,
        ),
        "BX" to listOf(
            30.560288 to 103.969303,
            30.560917 to 103.969303,
            30.561546 to 103.968572,
            30.562175 to 103.968572,
            30.562803 to 103.968572,
            30.563432 to 103.968572,
            30.564061 to 103.968573,
            30.564690 to 103.968573,
            30.565319 to 103.968573,
            30.565948 to 103.967842,
            30.566576 to 103.967842,
            30.567205 to 103.967842,
            30.567834 to 103.967842,
            30.568463 to 103.967843,
            30.569092 to 103.967843,
            30.569720 to 103.968573,
            30.570349 to 103.968573,
            30.570978 to 103.968573,
            30.571607 to 103.968573,
            30.572236 to 103.968573,
            30.572865 to 103.968573,
            30.573493 to 103.968573,
            30.574122 to 103.969304,
            30.574751 to 103.969304,
            30.574751 to 103.969304,
            30.574122 to 103.970034,
            30.573493 to 103.970034,
            30.572865 to 103.970764,
            30.572236 to 103.970764,
            30.571607 to 103.970764,
            30.570978 to 103.970764,
            30.570349 to 103.970764,
            30.569720 to 103.970764,
            30.569092 to 103.970764,
            30.568463 to 103.970764,
            30.567834 to 103.970764,
            30.567205 to 103.970764,
            30.566576 to 103.970764,
            30.565948 to 103.970764,
            30.565319 to 103.970764,
            30.564690 to 103.970763,
            30.564061 to 103.970763,
            30.563432 to 103.970763,
            30.562803 to 103.970763,
            30.562175 to 103.970763,
            30.561546 to 103.970033,
            30.560917 to 103.970033,
            30.560288 to 103.969303,
        ),
    )

    fun outlineFor(room: String): List<Pair<Double, Double>>? = forRoom(room)?.let { outlines[it.name] }

    fun forRoom(room: String): CampusFence? {
        val u = room.trim().uppercase().replace(" ", "")
        if (u.isEmpty()) return null
        if (Regex("(^|[^A-Z])BS-?\\d").containsMatchIn(u)) return bs
        if (Regex("(^|[^A-Z])BW-?\\d").containsMatchIn(u)) return bw
        if (Regex("(^|[^A-Z])BX-?\\d").containsMatchIn(u)) return bx
        if (Regex("(^|[^A-Z])H-?\\d").containsMatchIn(u)) return h
        return null
    }
}
