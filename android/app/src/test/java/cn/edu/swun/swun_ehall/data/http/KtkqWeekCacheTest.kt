package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.KtkqCourse
import cn.edu.swun.swun_ehall.data.model.KtkqWeek
import org.junit.Assert.*
import org.junit.Test

class KtkqWeekCacheTest {
    @Test fun cachedCoursesCannotCrossAccountSemesterOrWeek() {
        val cache = KtkqWeekCache()
        val courses = KtkqWeek(week = 6, courses = listOf(KtkqCourse("course-a")))
        cache.put("student-a", "2026-1", courses)
        assertSame(courses, cache.get("student-a", "2026-1", 6))
        assertNull(cache.get("student-b", "2026-1", 6))
        assertNull(cache.get("student-a", "2026-2", 6))
        assertNull(cache.get("student-a", "2026-1", 7))
        cache.clear()
        assertNull(cache.get("student-a", "2026-1", 6))
    }

    @Test fun emptyResultsDoNotSuppressTheNextRefresh() {
        val cache = KtkqWeekCache()
        cache.put("a", "term", KtkqWeek(week = 1))
        assertNull(cache.get("a", "term", 1))
    }
}
