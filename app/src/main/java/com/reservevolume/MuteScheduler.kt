package com.reservevolume

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
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

    /** 볼륨 단계를 %로(volumeLevel의 반대). 최대 15단계면 7 → 47 */
    fun levelPercent(max: Int, level: Int) = (level * 100 + max / 2) / max

    /** 기기의 음악 볼륨 최대 단계. 갤럭시는 보통 15 */
    fun maxLevel(c: Context) = c.getSystemService(AudioManager::class.java).getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /** 예약을 지우고 걸려 있던 알람도 취소한다. */
    fun delete(c: Context, key: String) {
        prefs(c).edit().remove(key).apply()
        repeatPrefs(c).edit().remove(key).apply()
        volumePrefs(c).edit().remove(key).apply()
        c.getSystemService(AlarmManager::class.java).cancel(pending(c, key))
    }

    /** 예약을 등록하거나 old를 고친다. 등록·수정하면 바로 켠다. */
    fun save(c: Context, old: String?, key: String, days: Int, volume: Int) {
        if (old != null) delete(c, old)
        prefs(c).edit().putBoolean(key, true).apply()
        repeatPrefs(c).edit().putInt(key, days).apply()
        volumePrefs(c).edit().putInt(key, volume).apply()
        apply(c)
    }

    /** 정확한 시각 알람을 걸 수 있는지. Android 12에서 사용자가 "알람 및 리마인더" 권한을 끄면 false */
    fun canExact(c: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** 예약마다 다음 알람을 걸거나(켜짐) 취소한다(꺼짐). */
    fun apply(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val exact = canExact(c)
        val now = ZonedDateTime.now()
        for ((key, on) in prefs(c).all) {
            if (on != true) {
                am.cancel(pending(c, key))
                continue
            }
            val at = nextTrigger(now, LocalTime.parse(key), daysOf(c, key)).toInstant().toEpochMilli()
            // 권한이 없으면 정확한 알람은 예외로 앱이 죽는다. 몇 분 늦을 수 있는 알람으로라도 건다
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, key))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, key))
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
