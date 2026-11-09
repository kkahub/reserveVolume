package com.reservevolume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/** 예약 시각에 음악 볼륨을 예약한 값으로 맞추고, 재부팅 시엔 알람을 다시 건다(재부팅하면 알람이 사라지므로). */
class MuteReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action == MuteScheduler.ACTION_MUTE) {
            val key = intent.getStringExtra(MuteScheduler.EXTRA_KEY)
            val percent = key?.let { MuteScheduler.volumeOf(c, it) } ?: 0
            val audio = c.getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, MuteScheduler.volumeLevel(max, percent), 0)
            // 요일을 안 고른(한 번만) 예약은 울린 뒤 끈다
            if (key != null && MuteScheduler.daysOf(c, key) == 0) {
                MuteScheduler.prefs(c).edit().putBoolean(key, false).apply()
            }
        }
        MuteScheduler.apply(c) // 다음 알람 재예약
    }
}
