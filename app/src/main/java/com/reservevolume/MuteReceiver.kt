package com.reservevolume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import kotlin.concurrent.thread

/** 예약 시각에 음악 볼륨을 예약한 값으로 맞추고, 재부팅 시엔 알람을 다시 건다(재부팅하면 알람이 사라지므로). */
class MuteReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action == MuteScheduler.ACTION_MUTE) {
            val key = intent.getStringExtra(MuteScheduler.EXTRA_KEY)
            val percent = key?.let { MuteScheduler.volumeOf(c, it) } ?: 0
            val level = MuteScheduler.volumeLevel(MuteScheduler.maxLevel(c), percent)
            val audio = c.getSystemService(AudioManager::class.java)
            if (key != null && MuteScheduler.fadeOf(c, key)) {
                // 페이드: 한 단계씩, 8단계 이상 바뀌면 0.05초 아니면 0.1초 간격. 끝날 때까지 브로드캐스트를 붙잡아야
                // 알람 wake lock이 유지돼 화면이 꺼져 있어도 이어진다.
                // shortcut: 브로드캐스트는 약 10초 안에 끝나야 해서 200단계 넘게 바뀌면(0.05초×200) 잘릴 수 있다, 그런 기기가 나오면 서비스로 옮긴다
                val done = goAsync()
                thread {
                    try {
                        val levels = MuteScheduler.fadeLevels(audio.getStreamVolume(AudioManager.STREAM_MUSIC), level)
                        val gap = if (levels.size >= 8) 50L else 100L
                        for (l in levels) {
                            Thread.sleep(gap)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, l, 0)
                        }
                    } finally {
                        done.finish()
                    }
                }
            } else {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
            }
            // 요일을 안 고른(한 번만) 예약은 울린 뒤 끈다
            if (key != null && MuteScheduler.daysOf(c, key) == 0) {
                MuteScheduler.prefs(c).edit().putBoolean(key, false).apply()
            }
        }
        MuteScheduler.apply(c) // 다음 알람 재예약
    }
}
