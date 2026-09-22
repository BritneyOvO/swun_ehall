package cn.edu.swun.swun_ehall.data.session

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import cn.edu.swun.swun_ehall.data.model.Lesson
import org.json.JSONObject

class SlotChoiceStore(private val prefs: SharedPreferences) {
    val picks = mutableStateMapOf<String, String>()
    var generation by mutableIntStateOf(0)
        private set
    private var owner = ""

    fun load(studentId: String) {
        owner = studentId.trim()
        picks.clear()
        val raw = prefs.getString(prefKey(), "").orEmpty()
        if (raw.isBlank()) {
            bump()
            return
        }
        try {
            val o = JSONObject(raw)
            o.keys().forEach { k -> picks[k] = o.optString(k) }
            compactWeekKeys()
        } catch (_: Exception) {
        }
        bump()
    }

    fun lessonId(l: Lesson): String =
        listOf(l.name, l.room, l.teacher, "${l.weekday}", "${l.start}", "${l.span}", l.weeks).joinToString("|")

    fun slotKey(group: List<Lesson>): String {
        val day = group.first().weekday
        val start = group.minOf { it.start }
        val end = group.maxOf { it.end }
        return "$day|$start|$end"
    }

    fun groups(dayLessons: List<Lesson>): List<List<Lesson>> {
        if (dayLessons.isEmpty()) return emptyList()
        val items = dayLessons
        val used = BooleanArray(items.size)
        val out = mutableListOf<List<Lesson>>()
        for (i in items.indices) {
            if (used[i]) continue
            val cluster = mutableListOf(items[i])
            used[i] = true
            var grew = true
            while (grew) {
                grew = false
                for (j in items.indices) {
                    if (used[j]) continue
                    if (cluster.any { overlap(it, items[j]) }) {
                        cluster.add(items[j])
                        used[j] = true
                        grew = true
                    }
                }
            }
            out.add(cluster.sortedWith(compareBy({ it.start }, { it.name }, { it.teacher })))
        }
        return out
    }

    fun chosen(week: Int, group: List<Lesson>): Lesson? {
        if (group.size <= 1) return group.firstOrNull()
        val id = picks[slotKey(group)]
            ?: picks["$week|${slotKey(group)}"]
            ?: return null
        return matchChosen(group, id)
    }

    fun pick(week: Int, group: List<Lesson>, lesson: Lesson) {
        val key = slotKey(group)
        val stale = picks.keys.filter { it != key && (it.endsWith("|$key") || it == "$week|$key") }
        stale.forEach { picks.remove(it) }
        picks[key] = lessonId(lesson)
        save()
        bump()
    }

    fun resolve(lessons: List<Lesson>, week: Int): List<Lesson> {
        val out = mutableListOf<Lesson>()
        lessons.groupBy { it.weekday }.values.forEach { day ->
            groups(day).forEach { g ->
                if (g.size == 1) out.add(g.first())
                else chosen(week, g)?.let { out.add(it) }
            }
        }
        return out.sortedWith(compareBy({ it.weekday }, { it.start }))
    }

    private fun bump() {
        generation++
    }

    private fun compactWeekKeys() {
        val weekPrefixed = Regex("""^\d+\|(\d+\|\d+\|\d+)$""")
        val extras = mutableListOf<String>()
        picks.keys.toList().forEach { k ->
            val m = weekPrefixed.matchEntire(k) ?: return@forEach
            val bare = m.groupValues[1]
            if (bare !in picks) picks[bare] = picks[k].orEmpty()
            extras.add(k)
        }
        if (extras.isEmpty()) return
        extras.forEach { picks.remove(it) }
        save()
    }

    private fun save() {
        val o = JSONObject()
        picks.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(prefKey(), o.toString()).apply()
    }

    private fun prefKey() = "slot_picks_${owner.ifBlank { "anon" }}"

    companion object {
        fun overlap(a: Lesson, b: Lesson) = a.start <= b.end && b.start <= a.end

        fun matchChosen(group: List<Lesson>, id: String): Lesson? {
            if (group.isEmpty() || id.isBlank()) return null
            group.firstOrNull { lessonIdOf(it) == id }?.let { return it }
            val parts = id.split('|')
            val name = parts.getOrNull(0).orEmpty()
            val room = parts.getOrNull(1).orEmpty()
            val teacher = parts.getOrNull(2).orEmpty()
            val same = group.filter { it.name == name && (room.isEmpty() || it.room == room) }
            if (same.isEmpty()) return null
            if (same.size == 1) return same.first()
            same.firstOrNull { it.teacher == teacher && teacher.isNotEmpty() }?.let { return it }
            return same.minBy { TeacherCache.parseWeekSet(skzc = it.weeks).size.coerceAtLeast(1) }
        }

        fun lessonIdOf(l: Lesson): String =
            listOf(l.name, l.room, l.teacher, "${l.weekday}", "${l.start}", "${l.span}", l.weeks).joinToString("|")
    }
}
