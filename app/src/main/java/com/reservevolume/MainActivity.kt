package com.reservevolume

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.reservevolume.MuteScheduler.EVERY_DAY
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 12시간제(오전 0/오후 1, 1~12시) → 24시간제. 오전 12시 = 0시 */
internal fun to24h(pm: Int, hour12: Int) = hour12 % 12 + pm * 12
internal fun to12h(hour24: Int) = (hour24 + 11) % 12 + 1

// 목록·경고 표기: 오전/오후 + 08:07 (팝업과 같은 12시간제)
private val AMPM = DateTimeFormatter.ofPattern("a", Locale.KOREAN)
private val CLOCK = DateTimeFormatter.ofPattern("hh:mm")

private const val DAYS = "일월화수목금토" // 요일 비트 순서와 같다

/** 목록에 보일 반복 주기: 매일 / 한 번 / 월 수 금 */
internal fun repeatLabel(days: Int) = when (days) {
    EVERY_DAY -> "매일"
    0 -> "한 번"
    else -> DAYS.filterIndexed { i, _ -> days and (1 shl i) != 0 }.toList().joinToString(" ")
}

internal fun volumeLabel(percent: Int) = if (percent == 0) "음소거" else "$percent%"

class MainActivity : Activity() {
    private val prefs by lazy { MuteScheduler.prefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.add).setOnClickListener { pickTime(null) }
    }

    // 한 번만 예약이 울리고 꺼진 걸 앱으로 돌아올 때 반영한다
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val list = findViewById<ViewGroup>(R.id.list)
        list.removeAllViews()
        val keys = prefs.all.keys.sorted() // "HH:mm"이라 문자열 정렬이 곧 시간순
        findViewById<View>(R.id.empty).visibility = if (keys.isEmpty()) View.VISIBLE else View.GONE
        for (key in keys) {
            val item = layoutInflater.inflate(R.layout.item_reservation, list, false)
            val t = LocalTime.parse(key)
            item.findViewById<TextView>(R.id.ampm).text = t.format(AMPM)
            item.findViewById<TextView>(R.id.time).text = t.format(CLOCK)
            item.findViewById<TextView>(R.id.repeat).text = repeatLabel(MuteScheduler.daysOf(this, key))
            val percent = MuteScheduler.volumeOf(this, key)
            item.findViewById<TextView>(R.id.volume).apply {
                text = volumeLabel(percent)
                val icon = if (percent == 0) R.drawable.ic_speaker_off else R.drawable.ic_speaker
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            }
            val content = item.findViewById<View>(R.id.content)
            fun showEnabled(on: Boolean) { content.alpha = if (on) 1f else 0.4f } // 꺼진 예약은 글씨·아이콘을 어둡게
            item.findViewById<Switch>(R.id.enabled).apply {
                isChecked = prefs.getBoolean(key, false)
                showEnabled(isChecked)
                setOnCheckedChangeListener { _, on ->
                    showEnabled(on)
                    prefs.edit().putBoolean(key, on).apply()
                    MuteScheduler.apply(this@MainActivity)
                }
            }
            item.setOnClickListener { pickTime(key) }
            list.addView(item)
        }
    }

    /** 원UI 알람처럼 위아래로 넘기거나 가운데 숫자를 눌러 직접 입력한다. old가 있으면 그 예약을 고친다. */
    private fun pickTime(old: String?) {
        val view = layoutInflater.inflate(R.layout.dialog_time, null)
        val ampm = view.findViewById<NumberPicker>(R.id.ampm)
        val hour = view.findViewById<NumberPicker>(R.id.hour)
        val minute = view.findViewById<NumberPicker>(R.id.minute)
        ampm.maxValue = 1
        ampm.displayedValues = arrayOf("오전", "오후")
        ampm.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS // 오전/오후는 넘기기만
        // 직접 입력 검증은 NumberPicker가 한다: 범위를 넘는 숫자는 입력 자체가 막히고, 빈 값은 이전 값으로 돌아간다
        hour.minValue = 1
        hour.maxValue = 12
        minute.maxValue = 59
        minute.setFormatter { "%02d".format(it) }
        // 키보드 "다음"으로 분 입력칸으로. 기본 다음 포커스는 분 NumberPicker 자체라 입력이 안 된다
        val minuteInput = minute.getChildAt(0)
        (hour.getChildAt(0) as EditText).apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setOnEditorActionListener { _, _, _ -> minuteInput.requestFocus() }
        }

        val t = old?.let(LocalTime::parse) ?: LocalTime.of(22, 0)
        ampm.value = t.hour / 12
        hour.value = to12h(t.hour)
        minute.value = t.minute

        // 일~토 토글. 새 예약은 미선택(한 번만)으로 시작
        val daysRow = view.findViewById<ViewGroup>(R.id.days)
        val dayBoxes = (0 until 7).map { daysRow.getChildAt(it) as CheckBox }
        val oldDays = old?.let { MuteScheduler.daysOf(this, it) } ?: 0
        dayBoxes.forEachIndexed { i, box -> box.isChecked = oldDays and (1 shl i) != 0 }

        // 볼륨 % (SeekBar 0~100). 새 예약은 음소거로 시작
        val volume = view.findViewById<SeekBar>(R.id.volume)
        val volumeText = view.findViewById<TextView>(R.id.volume_text)
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                volumeText.text = volumeLabel(progress)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
        volume.progress = old?.let { MuteScheduler.volumeOf(this, it) } ?: 0
        volumeText.text = volumeLabel(volume.progress) // 0이면 위 리스너가 안 불려서 직접 채운다

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장", null)
            .show()
        // 중복일 때 창을 닫지 않으려고 저장 클릭을 직접 처리한다
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            view.requestFocus() // 입력 중인 칸의 포커스를 빼야 NumberPicker가 값을 확정한다
            val time = LocalTime.of(to24h(ampm.value, hour.value), minute.value)
            val key = time.toString()
            if (key != old && prefs.contains(key)) {
                AlertDialog.Builder(this)
                    .setMessage("${time.format(AMPM)} ${time.format(CLOCK)}에 이미 예약이 있어요.")
                    .setPositiveButton("확인", null)
                    .show()
                return@setOnClickListener
            }
            val days = dayBoxes.foldIndexed(0) { i, acc, box -> if (box.isChecked) acc or (1 shl i) else acc }
            MuteScheduler.save(this, old, key, days, volume.progress)
            dialog.dismiss()
            render()
        }
    }
}
