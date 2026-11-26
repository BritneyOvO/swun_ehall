package cn.edu.swun.swun_ehall.data.model

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BusTest {
    @Test fun nextDepartureIncludesExactBoundaryAndCalculatesCountdown() {
        val times = listOf(LocalTime.of(7, 40), LocalTime.of(9, 40))
        assertEquals(NextBus(LocalTime.of(7, 40), 0), nextBus(times, LocalTime.of(7, 40)))
        assertEquals(NextBus(LocalTime.of(9, 40), 119), nextBus(times, LocalTime.of(7, 41)))
        assertNull(nextBus(times, LocalTime.of(21, 0)))
    }

    @Test fun directionAndDaySelectDifferentOfficialSchedules() {
        val line = swunBusTimetables.first()
        assertEquals(LocalTime.of(7, 40), line.times(BusDayType.Weekday, reverse = false).first())
        assertEquals(LocalTime.of(22, 30), line.times(BusDayType.Weekday, reverse = true).last())
        assertEquals(LocalTime.of(8, 30), line.times(BusDayType.Holiday, reverse = false).first())
    }
}
