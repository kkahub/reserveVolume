package com.reservevolume

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalTime
import java.time.ZonedDateTime

object MuteScheduler {
    const val ACTION_MUTE = "com.reservevolume.MUTE"
    const val EXTRA_KEY = "key"
    /** 반복 요일 비트: bit0=일 … bit6=토. 0이면 한 번만 */
    const val EVERY_DAY = 0x7F

    /** 예약 목록. 키 "HH:mm" → 켜짐 여부 */
    fun prefs(c: Context) = c.getSharedPreferences("reservations", Context.MODE_PRIVATE)

    /** 반복 요일. 키 "HH:mm" → 요일 비트 */
    private fun repeatPrefs(c: Context) = c.getSharedPreferences("days", Context.MODE_PRIVATE)

    fun daysOf(c: Context, key: String) = repeatPrefs(c).getInt(key, EVERY_DAY) // 요일 기능 전 예약은 매일

    /** 예약 시각에 맞출 음악 볼륨 %. 키 "HH:mm" → 0~100 (0 = 음소거) */
    private fun volumePrefs(c: Context) = c.getSharedPreferences("volume", Context.MODE_PRIVATE)

    fun volumeOf(c: Context, key: String) = volumePrefs(c).getInt(key, 0) // 볼륨 기능 전 예약은 음소거

    /** 볼륨 %를 기기 볼륨 단계로 반올림. 최대 15단계면 60% → 9 */
    fun volumeLevel(max: Int, percent: Int) = (max * percent + 50) / 100

    /** 예약을 등록하거나 old를 고친다. 등록·수정하면 바로 켠다. */
    fun save(c: Context, old: String?, key: String, days: Int, volume: Int) {
        if (old != null) {
            prefs(c).edit().remove(old).apply()
            repeatPrefs(c).edit().remove(old).apply()
            volumePrefs(c).edit().remove(old).apply()
            c.getSystemService(AlarmManager::class.java).cancel(pending(c, old))
        }
        prefs(c).edit().putBoolean(key, true).apply()
        repeatPrefs(c).edit().putInt(key, days).apply()
        volumePrefs(c).edit().putInt(key, volume).apply()
        apply(c)
    }

    /** 예약마다 다음 알람을 걸거나(켜짐) 취소한다(꺼짐). */
    fun apply(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        for ((key, on) in prefs(c).all) {
            if (on != true) {
                am.cancel(pending(c, key))
                continue
            }
            val at = nextTrigger(now, LocalTime.parse(key), daysOf(c, key))
            // shortcut: Android 13+ 기기에선 정확한 알람 권한 미허용 시 예외, 노트9 외 기기에 쓸 때 canScheduleExactAlarms() 체크 추가
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending(c, key))
        }
    }

    // 시각마다 requestCode를 달리해 알람끼리 덮어쓰지 않게 한다
    private fun pending(c: Context, key: String) = PendingIntent.getBroadcast(
        c, LocalTime.parse(key).toSecondOfDay() / 60,
        Intent(c, MuteReceiver::class.java).setAction(ACTION_MUTE).putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** now 이후 가장 가까운 time 중 반복 요일에 맞는 것. 같은 분이면 다음으로 넘긴다. days=0(한 번)은 요일 무관 */
    fun nextTrigger(now: ZonedDateTime, time: LocalTime, days: Int = EVERY_DAY): ZonedDateTime {
        val today = now.with(time.withSecond(0).withNano(0))
        return (0L..7L).map { today.plusDays(it) }.first {
            it.isAfter(now) && (days == 0 || days and (1 shl it.dayOfWeek.value % 7) != 0)
        }
    }
}
