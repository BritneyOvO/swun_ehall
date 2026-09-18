package cn.edu.swun.swun_ehall.data.fences

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
)

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

    fun forRoom(room: String): CampusFence? {
        val u = room.trim().uppercase().replace(" ", "")
        if (u.isEmpty()) return null
        if (Regex("^BS-?\\d").containsMatchIn(u)) return bs
        if (Regex("^BW-?\\d").containsMatchIn(u)) return bw
        if (Regex("^BX-?\\d").containsMatchIn(u)) return bx
        if (Regex("^H-?\\d").containsMatchIn(u)) return h
        return null
    }
}
