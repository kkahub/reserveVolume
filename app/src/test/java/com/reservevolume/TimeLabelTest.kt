package com.reservevolume

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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
        // 일부 요일이면 일~토 전체를 쓰고 고른 요일 강조는 화면에서 입힌다(i번째 요일 = i*2 위치)
        val monWedFri = repeatLabel(1 shl 1 or (1 shl 3) or (1 shl 5), tue)
        assertEquals("일 월 화 수 목 금 토", monWedFri)
        assertEquals("월수금", listOf(1, 3, 5).map { monWedFri[it * 2] }.joinToString(""))
    }

    @Test fun hourInputDoneAfterTwoDigitsOr2to9() {
        assertEquals(false, hourInputDone(""))
        assertEquals(false, hourInputDone("1")) // 10~12시가 될 수 있다
        assertEquals(false, hourInputDone("0"))
        assertEquals(true, hourInputDone("10"))
        assertEquals(true, hourInputDone("07"))
        assertEquals(true, hourInputDone("7")) // 70시는 없으니 바로 끝
    }

    @Test fun firstRunMessages() {
        val seoul = ZoneId.of("Asia/Seoul")
        fun at(d: Int, h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 11, d, h, m, s, 0, seoul)
        val now = at(10, 9, 10, 30) // 11/10(화) 09:10:30
        assertEquals("22시간 50분 후 예약이 실행됩니다.", firstRunMessage(now, at(11, 8, 0))) // 22시간 49분 30초 → 분 올림
        assertEquals("1시간 후 예약이 실행됩니다.", firstRunMessage(now, at(10, 10, 10, 30)))
        assertEquals("1분 후 예약이 실행됩니다.", firstRunMessage(now, at(10, 9, 11)))
        assertEquals("11월 12일 (목) 오전 08:00에 예약이 실행됩니다.", firstRunMessage(now, at(12, 8, 0))) // 24시간 넘으면 날짜
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
