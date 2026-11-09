package com.reservevolume

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TimeLabelTest {
    @Test fun midnightAndNoon() {
        assertEquals(0, to24h(0, 12))  // 오전 12시
        assertEquals(12, to24h(1, 12)) // 오후 12시
        assertEquals(12, to12h(0))
        assertEquals(12, to12h(12))
    }

    @Test fun roundTripAllHours() {
        for (h in 0..23) assertEquals(h, to24h(h / 12, to12h(h)))
    }

    @Test fun repeatLabels() {
        val tue = LocalDate.of(2026, 11, 10)
        assertEquals("매일", repeatLabel(MuteScheduler.EVERY_DAY, tue))
        assertEquals("11월 10일 (화)", repeatLabel(0, tue)) // 한 번만이면 울릴 날짜
        assertEquals("월 수 금", repeatLabel(1 shl 1 or (1 shl 3) or (1 shl 5), tue))
    }

    @Test fun dragSelectRange() {
        val none = List(7) { false }
        // 월(1)에서 수(3)까지 끌면 월~수가 켜진다
        assertEquals(listOf(false, true, true, true, false, false, false), dragSelect(none, 1, 3))
        // 거꾸로 끌어도 같은 범위, 범위 밖은 원래대로
        assertEquals(listOf(false, true, true, true, false, false, false), dragSelect(none, 3, 1))
        // 켜진 칸에서 시작하면 범위를 끈다
        val monToFri = listOf(false, true, true, true, true, true, false)
        assertEquals(listOf(false, false, false, true, true, true, false), dragSelect(monToFri, 1, 2))
    }

    @Test fun volumeLabels() {
        assertEquals("음소거", volumeLabel(0))
        assertEquals("1%", volumeLabel(1))
        assertEquals("65%", volumeLabel(65))
        assertEquals("100%", volumeLabel(100))
    }
}
