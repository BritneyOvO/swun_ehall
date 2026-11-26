package cn.edu.swun.swun_ehall.data.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolTermTest {
    @Test fun currentTermFollowsAcademicYearBoundaries() {
        assertEquals(SchoolTerm("2026", "3"), currentSchoolTerm(LocalDate.of(2027, 1, 15)))
        assertEquals(SchoolTerm("2026", "12"), currentSchoolTerm(LocalDate.of(2027, 2, 1)))
        assertEquals(SchoolTerm("2026", "12"), currentSchoolTerm(LocalDate.of(2027, 7, 31)))
        assertEquals(SchoolTerm("2027", "3"), currentSchoolTerm(LocalDate.of(2027, 8, 1)))
    }
}
