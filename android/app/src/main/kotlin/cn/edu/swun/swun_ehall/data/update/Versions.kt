package cn.edu.swun.swun_ehall.data.update

object Versions {
    const val APP = "1.0.5"

    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun isNewer(remote: String, current: String = APP): Boolean = compare(remote, current) > 0

    private fun parts(raw: String): List<Int> {
        var s = raw.trim()
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1)
        s = s.split("+").first().split("-").first()
        if (s.isEmpty()) return listOf(0)
        return s.split(".").map { it.toIntOrNull() ?: 0 }
    }
}
