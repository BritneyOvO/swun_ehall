package cn.edu.swun.swun_ehall.data.update

import cn.edu.swun.swun_ehall.BuildConfig
import cn.edu.swun.swun_ehall.data.model.AppRelease

object Versions {
    val APP: String = BuildConfig.VERSION_NAME
    val CODE: Int = BuildConfig.VERSION_CODE

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

    fun isNewer(rel: AppRelease): Boolean {
        if (rel.versionCode > 0) return rel.versionCode > CODE
        return isNewer(rel.version, APP)
    }

    private fun parts(raw: String): List<Int> {
        var s = raw.trim()
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1)
        s = s.split("+").first().split("-").first()
        if (s.isEmpty()) return listOf(0)
        return s.split(".").map { it.toIntOrNull() ?: 0 }
    }
}
