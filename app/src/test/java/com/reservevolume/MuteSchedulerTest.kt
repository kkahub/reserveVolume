package com.reservevolume

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MuteSchedulerTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(h: Int, m: Int, s: Int = 0, day: Int = 5) = ZonedDateTime.of(2026, 11, day, h, m, s, 0, seoul)

    @Test fun laterToday() = assertEquals(at(22, 0), MuteScheduler.nextTrigger(at(21, 0), LocalTime.of(22, 0)))

    @Test fun alreadyPassedGoesTomorrow() =
        assertEquals(at(7, 30, day = 6), MuteScheduler.nextTrigger(at(8, 0), LocalTime.of(7, 30)))

    @Test fun sameMinuteGoesTomorrow() =
        assertEquals(at(22, 0, day = 6), MuteScheduler.nextTrigger(at(22, 0, 10), LocalTime.of(22, 0)))

    // 2026-11-09는 월요일. 요일 비트 bit0=일 … bit6=토
    private val mon = 1 shl 1
    private val wed = 1 shl 3

    @Test fun skipsToNextSelectedDay() =
        assertEquals(at(22, 0, day = 11), MuteScheduler.nextTrigger(at(21, 0, day = 9), LocalTime.of(22, 0), wed))

    @Test fun onlyTodayPassedGoesNextWeek() =
        assertEquals(at(22, 0, day = 16), MuteScheduler.nextTrigger(at(23, 0, day = 9), LocalTime.of(22, 0), mon))

    @Test fun volumePercentRoundsToDeviceLevel() {
        assertEquals(0, MuteScheduler.volumeLevel(15, 0))
        assertEquals(9, MuteScheduler.volumeLevel(15, 60))
        assertEquals(8, MuteScheduler.volumeLevel(15, 50)) // 7.5 → 8
        assertEquals(15, MuteScheduler.volumeLevel(15, 100))
    }

    @Test fun onceIgnoresWeekday() =
        assertEquals(at(22, 0, day = 10), MuteScheduler.nextTrigger(at(23, 0, day = 9), LocalTime.of(22, 0), 0))
}
