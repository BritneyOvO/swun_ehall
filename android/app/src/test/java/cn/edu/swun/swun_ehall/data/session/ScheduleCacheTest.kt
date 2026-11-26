package cn.edu.swun.swun_ehall.data.session

import cn.edu.swun.swun_ehall.data.model.Lesson
import cn.edu.swun.swun_ehall.data.model.SchoolTerm
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScheduleCacheTest {
    @get:Rule val temp = TemporaryFolder()

    private val lesson = Lesson("测试课程", "A101", "教师", 2, 3, 2, "11110000")
    private val term = SchoolTerm("2026", "3")

    @Test fun schedulesAreIsolatedByAccountAndSemester() {
        val cache = ScheduleCache(File(temp.root, "schedules"))
        val value = CachedSchedule(
            term,
            listOf(lesson),
            week = 4,
            totalWeeks = 18,
            firstMonday = LocalDate.of(2026, 8, 31),
            updatedAt = 1234L,
        )

        cache.save("student-a", value)

        assertEquals(value, cache.load("student-a", SchoolTerm("2026", "01")))
        assertNull(cache.load("student-b", term))
        assertNull(cache.load("student-a", SchoolTerm("2026", "12")))
    }

    @Test fun calendarWeekAdvancesAndClampsToSemesterBounds() {
        val cached = CachedSchedule(
            term,
            listOf(lesson),
            week = 6,
            totalWeeks = 16,
            firstMonday = LocalDate.of(2026, 8, 31),
            updatedAt = 0L,
        )

        assertEquals(1, cached.weekOn(LocalDate.of(2026, 8, 20)))
        assertEquals(1, cached.weekOn(LocalDate.of(2026, 8, 31)))
        assertEquals(7, cached.weekOn(LocalDate.of(2026, 10, 12)))
        assertEquals(16, cached.weekOn(LocalDate.of(2027, 2, 1)))
    }

    @Test fun damagedOrInvalidCacheIsIgnoredAndCanBeRemoved() {
        val directory = File(temp.root, "schedules")
        val cache = ScheduleCache(directory)
        cache.save(
            "student-a",
            CachedSchedule(term, listOf(lesson), 1, 16, null, 0L),
        )
        val file = directory.listFiles().orEmpty().single { it.extension == "json" }
        file.writeText("{broken")

        assertNull(cache.load("student-a", term))
        cache.remove("student-a")
        assertTrue(directory.listFiles().orEmpty().none { it.extension == "json" })
    }
}
