package cn.edu.swun.swun_ehall.data.http

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchoolCalendarParseTest {
    @Test fun parsesTermDatesTeachingDaysAndEvents() {
        val html = """
            <html><head><title>2026-2027学年第一学期校历</title></head><body>
            <input value="2026-08-31" type="hidden" id="ksrq">
            <input id='jsrq' value='2027-01-17'>
            <script>
              skrq.push('2026-08-31'); skrq.push("2026-09-01"); skrq.push('2026-08-31');
              rc.push({bgdate:'2026-10-01', eddate:'2026-10-07', title:'国庆节'});
              rc.push({bgdate:"2027-01-11",eddate:"2027-01-17",title:"考试周"});
            </script>
            </body></html>
        """.trimIndent()

        val calendar = parseSchoolCalendar(html)

        assertEquals(LocalDate.of(2026, 8, 31), calendar.startDate)
        assertEquals(LocalDate.of(2027, 1, 17), calendar.endDate)
        assertEquals(listOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)), calendar.classDays)
        assertEquals(listOf("国庆节", "考试周"), calendar.events.map { it.title })
        assertEquals(7, calendar.weekOf(LocalDate.of(2026, 10, 12)))
        assertNull(calendar.weekOf(LocalDate.of(2026, 8, 30)))
    }

    @Test fun teachingWeekExcludesTheRegistrationWeek() {
        val calendar = parseSchoolCalendar(
            """
            <input id="ksrq" value="2026-08-24">
            <input id="jsrq" value="2027-01-17">
            <script>skrq.push('2026-09-01'); skrq.push('2026-08-31');</script>
            """.trimIndent(),
        )

        assertEquals(LocalDate.of(2026, 8, 24), calendar.startDate)
        assertNull(calendar.weekOf(LocalDate.of(2026, 8, 29)))
        assertEquals(1, calendar.weekOf(LocalDate.of(2026, 8, 31)))
        assertEquals(13, calendar.weekOf(LocalDate.of(2026, 11, 26)))
        assertNull(calendar.weekOf(LocalDate.of(2027, 1, 18)))
    }
}
