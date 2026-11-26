package cn.edu.swun.swun_ehall.data.session

import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.SchoolTerm
import cn.edu.swun.swun_ehall.data.model.normalizeXqm
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

data class CachedSchedule(
    val term: SchoolTerm,
    val lessons: List<Lesson>,
    val week: Int,
    val totalWeeks: Int,
    val firstMonday: LocalDate?,
    val updatedAt: Long,
) {
    fun weekOn(date: LocalDate): Int = firstMonday?.let {
        (Math.floorDiv(ChronoUnit.DAYS.between(it, date), 7).toInt() + 1).coerceIn(1, totalWeeks)
    } ?: week.coerceIn(1, totalWeeks)
}

/** Successful schedules are isolated by account and semester, including after process death. */
class ScheduleCache(private val directory: File) {
    @Synchronized
    fun load(account: String, term: SchoolTerm): CachedSchedule? {
        if (account.isBlank()) return null
        return try {
            val file = fileFor(account)
            if (!file.exists()) return null
            val root = JSONObject(file.readText())
            if (root.optInt("version") != 1 || root.optString("account") != account.trim()) return null
            val savedTerm = SchoolTerm(root.getString("year"), root.getString("semester"))
            if (savedTerm.xnm != term.xnm || normalizeXqm(savedTerm.xqm) != normalizeXqm(term.xqm)) return null
            val list = root.getJSONArray("lessons")
            val lessons = (0 until list.length()).map { i ->
                val item = list.getJSONObject(i)
                Lesson(
                    item.getString("name"), item.optString("room"), item.optString("teacher"),
                    item.getInt("weekday"), item.getInt("start"), item.getInt("span"), item.optString("weeks"),
                )
            }
            if (lessons.isEmpty() || lessons.any { !valid(it) }) return null
            val total = root.getInt("totalWeeks")
            val week = root.getInt("week")
            if (total !in 1..100 || week !in 1..total) return null
            CachedSchedule(
                savedTerm, lessons, week, total,
                root.optString("firstMonday").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                root.getLong("updatedAt"),
            )
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun save(account: String, schedule: CachedSchedule) {
        require(account.isNotBlank() && schedule.term.xnm.isNotBlank() && schedule.term.xqm.isNotBlank())
        require(schedule.lessons.isNotEmpty() && schedule.lessons.all(::valid))
        require(schedule.totalWeeks in 1..100 && schedule.week in 1..schedule.totalWeeks)
        val root = JSONObject().apply {
            put("version", 1)
            put("account", account.trim())
            put("year", schedule.term.xnm)
            put("semester", normalizeXqm(schedule.term.xqm))
            put("week", schedule.week)
            put("totalWeeks", schedule.totalWeeks)
            put("firstMonday", schedule.firstMonday?.toString().orEmpty())
            put("updatedAt", schedule.updatedAt)
            put("lessons", JSONArray().apply {
                schedule.lessons.forEach { lesson ->
                    put(JSONObject().apply {
                        put("name", lesson.name); put("room", lesson.room); put("teacher", lesson.teacher)
                        put("weekday", lesson.weekday); put("start", lesson.start); put("span", lesson.span)
                        put("weeks", lesson.weeks)
                    })
                }
            })
        }
        check(directory.exists() || directory.mkdirs()) { "无法创建课表缓存目录" }
        val target = fileFor(account)
        val temporary = File(directory, target.name + ".tmp")
        temporary.writeText(root.toString())
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("无法保存课表缓存")
        }
    }

    @Synchronized
    fun remove(account: String) {
        if (account.isBlank()) return
        val file = fileFor(account)
        file.delete()
        File(directory, file.name + ".tmp").delete()
    }

    private fun fileFor(account: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(account.trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$key.json")
    }

    private fun valid(lesson: Lesson): Boolean = lesson.name.isNotBlank() && lesson.weekday in 1..7 &&
        lesson.start in 1..24 && lesson.span > 0 && lesson.end <= 24
}
