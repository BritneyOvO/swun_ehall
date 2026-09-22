package cn.edu.swun.swun_ehall.data.session

import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.SignActivity
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class TeacherCache(private val file: File) {
    private val slot = mutableMapOf<String, MutableList<TeacherHit>>()
    private val byName = mutableMapOf<String, String>()
    private var owner = ""

    fun load(studentId: String) {
        owner = studentId.trim()
        slot.clear()
        byName.clear()
        if (!file.exists()) return
        try {
            val raw = JSONObject(file.readText())
            val xh = raw.optString("xh")
            if (xh.isNotEmpty() && owner.isNotEmpty() && xh != owner) return
            raw.optJSONObject("slot")?.let { o ->
                o.keys().forEach { k ->
                    slot[k] = hitsFromJson(o.opt(k)).toMutableList()
                }
            }
            raw.optJSONObject("name")?.let { o ->
                o.keys().forEach { k ->
                    val v = cleanTeacher(o.optString(k))
                    if (v.isNotEmpty()) byName[k] = v
                }
            }
        } catch (_: Exception) {
        }
    }

    fun fillFromJwxt(kb: JSONArray) {
        val grouped = linkedMapOf<String, MutableList<TeacherHit>>()
        for (i in 0 until kb.length()) {
            val m = kb.optJSONObject(i) ?: continue
            val xm = teacherOf(m)
            if (!looksLikeTeacherName(xm)) continue
            val name = normKc(courseNameOf(m))
            if (name.isEmpty()) continue
            val day = weekdayOf(m)
            val start = startOf(m)
            if (day.isEmpty() || start.isEmpty()) continue
            val weeks = parseWeekSet(skzc = m.optString("skzc"), zcd = m.optString("zcd"))
            grouped.getOrPut("$name|$day|$start") { mutableListOf() }.add(TeacherHit(weeks, xm))
        }
        grouped.forEach { (k, hits) -> slot[k] = hits }
        recomputeByName()
        save()
    }

    fun fillFromKtkq(items: List<SignActivity>, week: Int = 0) {
        var n = 0
        for (a in items) {
            val xm = cleanTeacher(a.teacher)
            if (!looksLikeTeacherName(xm)) continue
            val name = normKc(a.course)
            if (name.isEmpty()) continue
            if (a.weekDay !in 1..7 || a.startNode <= 0) continue
            val key = "$name|${a.weekDay}|${a.startNode}"
            val hits = slot.getOrPut(key) { mutableListOf() }
            if (hits.any { it.weeks.isEmpty() || week <= 0 || week in it.weeks }) continue
            hits.add(TeacherHit(if (week > 0) setOf(week) else emptySet(), xm))
            n++
        }
        if (n > 0) {
            recomputeByName()
            save()
        }
    }

    fun apply(lessons: List<Lesson>): List<Lesson> {
        return lessons.map { l ->
            val name = normKc(l.name)
            val key = "$name|${l.weekday}|${l.start}"
            val t = pickTeacher(slot[key].orEmpty(), parseWeekSet(skzc = l.weeks))
                ?: byName[name].orEmpty()
            when {
                t.isEmpty() -> l
                t == l.teacher -> l
                else -> l.copy(teacher = t)
            }
        }
    }

    private fun recomputeByName() {
        val byCourse = linkedMapOf<String, MutableSet<String>>()
        slot.forEach { (k, hits) ->
            val name = k.substringBefore('|')
            hits.forEach { byCourse.getOrPut(name) { mutableSetOf() }.add(it.name) }
        }
        byName.keys.toList().forEach { k ->
            if (k !in byCourse) return@forEach
            val xs = byCourse[k].orEmpty()
            if (xs.size != 1) byName.remove(k)
        }
        byCourse.forEach { (n, xs) ->
            if (xs.size == 1) byName[n] = xs.first()
        }
    }

    private fun save() {
        val slotObj = JSONObject()
        slot.forEach { (k, hits) ->
            val arr = JSONArray()
            hits.forEach { h ->
                arr.put(
                    JSONObject()
                        .put("w", h.weeks.sorted().joinToString(","))
                        .put("xm", h.name),
                )
            }
            slotObj.put(k, arr)
        }
        val nameObj = JSONObject()
        byName.forEach { (k, v) -> nameObj.put(k, v) }
        file.writeText(
            JSONObject()
                .put("xh", owner)
                .put("slot", slotObj)
                .put("name", nameObj)
                .toString(),
        )
    }

    companion object {
        data class TeacherHit(val weeks: Set<Int>, val name: String)

        fun normKc(s: String) = s.replace(Regex("\\s+"), "").replace('（', '(').replace('）', ')')

        fun cleanTeacher(raw: String): String {
            var s = raw.trim()
            if (s.isEmpty() || s == "null" || s == "—" || s == "-") return ""
            val parts = s.split('/', '、', ';', '；')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "null" && !it.matches(Regex("^\\d+$")) }
            return parts.joinToString("/").ifBlank { "" }.let { t ->
                t.split('/', limit = 2).first().trim()
            }
        }

        fun looksLikeTeacherName(raw: String): Boolean {
            val parts = cleanTeacher(raw).split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return false
            return parts.all { singlePersonName(it) }
        }

        private fun singlePersonName(s: String): Boolean {
            if (s.contains("学院") || s.contains("教室") || s.contains("学期") || s.contains("实验楼")) return false
            if (s.matches(Regex("""[A-Za-z]{1,3}-?\d{2,4}"""))) return false
            return s.matches(Regex("""[\u4e00-\u9fff]{2,4}(·[\u4e00-\u9fff]{1,4})?"""))
        }

        fun courseNameOf(m: JSONObject): String {
            for (k in listOf("kcmc", "kcm")) {
                val s = m.opt(k)?.toString()?.trim().orEmpty()
                if (s.isNotEmpty() && s != "null") return s
            }
            return ""
        }

        fun teacherOf(m: JSONObject): String {
            for (k in listOf("xm", "jsxm")) {
                val s = fieldTeacher(m.opt(k))
                if (looksLikeTeacherName(s)) return cleanTeacher(s)
            }
            for (k in listOf("skjs", "jsxx")) {
                val s = fieldTeacher(m.opt(k))
                if (looksLikeTeacherName(s)) return cleanTeacher(s)
            }
            return ""
        }

        private fun fieldTeacher(v: Any?): String {
            if (v == null || v == JSONObject.NULL) return ""
            return when (v) {
                is JSONObject -> teacherOf(v)
                is JSONArray -> {
                    (0 until v.length()).map { i ->
                        v.optJSONObject(i)?.let { teacherOf(it) }.orEmpty().ifBlank { cleanTeacher(v.optString(i)) }
                    }.firstOrNull { looksLikeTeacherName(it) }.orEmpty()
                }
                else -> cleanTeacher(v.toString())
            }
        }

        fun weekdayOf(m: JSONObject): String {
            val v = m.opt("xqj") ?: m.opt("skxq") ?: return ""
            v.toString().toIntOrNull()?.let { return "$it" }
            return ""
        }

        fun startOf(m: JSONObject): String {
            val sk = m.opt("skjc")?.toString().orEmpty()
            if (sk.isNotEmpty() && sk != "null") return sk
            val jcs = m.opt("jcs")?.toString().orEmpty()
            return jcs.split(Regex("[-~]")).firstOrNull().orEmpty()
        }

        fun parseWeekSet(skzc: String = "", zcd: String = ""): Set<Int> {
            val mask = skzc.trim()
            if (mask.contains('1') && mask.all { it == '0' || it == '1' }) {
                return mask.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }.toSet()
            }
            val text = zcd.trim().ifBlank { skzc.trim() }
            if (text.isEmpty() || text == "null") return emptySet()
            val odd = text.contains("单")
            val even = text.contains("双") && !text.contains("单")
            val s = mutableSetOf<Int>()
            Regex("""(\d+)\s*[-~到至]\s*(\d+)""").findAll(text).forEach { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                for (w in a..b) {
                    if (odd && w % 2 == 0) continue
                    if (even && w % 2 == 1) continue
                    s.add(w)
                }
            }
            if (s.isEmpty()) {
                Regex("""(\d+)""").findAll(text).forEach { s.add(it.groupValues[1].toInt()) }
            }
            return s
        }

        fun pickTeacher(hits: List<TeacherHit>, lessonWeeks: Set<Int>): String? {
            if (hits.isEmpty()) return null
            val overlapping = hits.filter { hit ->
                hit.weeks.isEmpty() || lessonWeeks.isEmpty() || hit.weeks.any { it in lessonWeeks }
            }
            if (overlapping.isEmpty()) return null
            overlapping.firstOrNull { it.weeks.isNotEmpty() && it.weeks == lessonWeeks }?.let { return it.name }
            val subsets = overlapping.filter { it.weeks.isNotEmpty() && lessonWeeks.containsAll(it.weeks) }
            if (subsets.isNotEmpty()) return subsets.minBy { it.weeks.size }.name
            return overlapping.maxBy { it.weeks.intersect(lessonWeeks).size }.name
        }

        private fun hitsFromJson(v: Any?): List<TeacherHit> {
            when (v) {
                is JSONArray -> {
                    return (0 until v.length()).mapNotNull { i ->
                        val o = v.optJSONObject(i) ?: return@mapNotNull null
                        val xm = cleanTeacher(o.optString("xm"))
                        if (xm.isEmpty()) return@mapNotNull null
                        val weeks = o.optString("w").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
                        TeacherHit(weeks, xm)
                    }
                }
                is String -> {
                    val xm = cleanTeacher(v)
                    return if (xm.isEmpty()) emptyList() else listOf(TeacherHit(emptySet(), xm))
                }
            }
            return emptyList()
        }
    }
}
