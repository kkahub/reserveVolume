package com.reservevolume

import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals("매일", repeatLabel(MuteScheduler.EVERY_DAY))
        assertEquals("한 번", repeatLabel(0))
        assertEquals("월 수 금", repeatLabel(1 shl 1 or (1 shl 3) or (1 shl 5)))
    }

    @Test fun volumeLabels() {
        assertEquals("음소거", volumeLabel(0))
        assertEquals("1%", volumeLabel(1))
        assertEquals("65%", volumeLabel(65))
        assertEquals("100%", volumeLabel(100))
    }
}
