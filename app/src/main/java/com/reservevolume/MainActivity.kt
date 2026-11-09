package com.reservevolume

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 12시간제(오전 0/오후 1, 1~12시) → 24시간제. 오전 12시 = 0시 */
internal fun to24h(pm: Int, hour12: Int) = hour12 % 12 + pm * 12
internal fun to12h(hour24: Int) = (hour24 + 11) % 12 + 1

// 목록·경고 표기: 오전/오후 + 08:07 (팝업과 같은 12시간제)
private val AMPM = DateTimeFormatter.ofPattern("a", Locale.KOREAN)
private val CLOCK = DateTimeFormatter.ofPattern("hh:mm")

private const val DAYS = "일월화수목금토" // 요일 비트 순서와 같다
private val ONCE_DATE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN) // 원UI처럼 11월 10일 (화)

/** 목록에 보일 반복 주기: 매일 / 월 수 금 / 한 번만이면 울릴 날짜(onceDate) */
internal fun repeatLabel(days: Int, onceDate: LocalDate) = when (days) {
    EVERY_DAY -> "매일"
    0 -> onceDate.format(ONCE_DATE)
    else -> DAYS.filterIndexed { i, _ -> days and (1 shl i) != 0 }.toList().joinToString(" ")
}

internal fun volumeLabel(percent: Int) = if (percent == 0) "음소거" else "$percent%"

/** 요일 드래그 선택: from~to 범위는 시작 칸의 반대 상태로, 범위 밖은 드래그 전 상태로 */
internal fun dragSelect(before: List<Boolean>, from: Int, to: Int): List<Boolean> {
    val range = minOf(from, to)..maxOf(from, to)
    return before.mapIndexed { i, on -> if (i in range) !before[from] else on }
}

/** 메시지 창(삭제 확인·중복 경고)을 띄운다. 글 영역에 최소 높이와 여백을 줘서 세로 가운데에 놓는다 */
private fun AlertDialog.Builder.showMessage(): AlertDialog = show().apply {
    findViewById<TextView>(android.R.id.message)?.apply {
        val dp = resources.displayMetrics.density
        minHeight = (96 * dp).toInt()
        gravity = Gravity.CENTER_VERTICAL
        setPadding(paddingLeft, (12 * dp).toInt(), paddingRight, (12 * dp).toInt())
    }
}

/** "22:00" → "오후 10:00" */
private fun timeLabel(t: LocalTime) = "${t.format(AMPM)} ${t.format(CLOCK)}"

class MainActivity : Activity() {
    private val prefs by lazy { MuteScheduler.prefs(this) }

    /** 선택(삭제) 모드에서 고른 예약들. null이면 일반 모드 */
    private var selection: MutableSet<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.add).setOnClickListener { pickTime(null) }
        findViewById<View>(R.id.select_all).setOnClickListener {
            val sel = selection ?: return@setOnClickListener
            val keys = prefs.all.keys
            if (sel.size == keys.size) sel.clear() else sel.addAll(keys)
            render()
        }
        findViewById<View>(R.id.cancel).setOnClickListener { exitSelection() }
        findViewById<View>(R.id.delete).setOnClickListener { confirmDelete() }
    }

    // 선택 모드에선 뒤로가기로 선택 모드만 빠져나온다
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (selection == null) super.onBackPressed() else exitSelection()
    }

    private fun exitSelection() {
        selection = null
        render()
    }

    // 한 번만 예약이 울리고 꺼진 걸 앱으로 돌아올 때 반영한다.
    // 알람도 다시 건다: 권한 설정에서 돌아왔거나, 재설치 후 백업에서 예약이 복원된 경우
    override fun onResume() {
        super.onResume()
        MuteScheduler.apply(this)
        render()
        guideSettings()
    }

    private var guide: AlertDialog? = null

    /** 예약이 제때 울리는 데 필요한 설정 안내. 정확한 알람 권한은 꺼져 있는 동안 매번, 절전 예외는 처음 한 번만 */
    private fun guideSettings() {
        if (guide?.isShowing == true) return
        val app = getSharedPreferences("app", MODE_PRIVATE)
        guide = when {
            !MuteScheduler.canExact(this) -> AlertDialog.Builder(this)
                .setMessage("정해진 시각에 바로 볼륨을 바꾸려면\n'알람 및 리마인더' 권한을 허용해 주세요.")
                .setNegativeButton("나중에", null)
                .setPositiveButton("설정 열기") { _, _ ->
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
                .showMessage()
            !app.getBoolean("battery_guided", false) &&
                !getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) -> {
                app.edit().putBoolean("battery_guided", true).apply()
                AlertDialog.Builder(this)
                    .setMessage(
                        "앱을 오래 열지 않아도 예약이 울리도록 배터리 최적화에서 빼 주세요.\n\n" +
                            "삼성 폰은 설정 > 배터리 > 백그라운드 사용 제한에서 '절전 예외 앱'에도 추가해 주세요."
                    )
                    .setNegativeButton("나중에", null)
                    .setPositiveButton("허용하기") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                    }
                    .showMessage()
            }
            else -> null
        }
    }

    private fun render() {
        val list = findViewById<ViewGroup>(R.id.list)
        list.removeAllViews()
        val keys = prefs.all.keys.sorted() // "HH:mm"이라 문자열 정렬이 곧 시간순
        findViewById<View>(R.id.empty).visibility = if (keys.isEmpty()) View.VISIBLE else View.GONE

        val sel = selection
        findViewById<View>(R.id.title).visibility = if (sel == null) View.VISIBLE else View.INVISIBLE // 자리는 유지
        findViewById<View>(R.id.selection_bar).visibility = if (sel == null) View.GONE else View.VISIBLE
        findViewById<View>(R.id.add).visibility = if (sel == null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.delete).visibility = if (sel == null) View.GONE else View.VISIBLE
        if (sel != null) {
            findViewById<CheckBox>(R.id.select_all).isChecked = sel.size == keys.size
            findViewById<View>(R.id.delete).isEnabled = sel.isNotEmpty() // 비활성 색은 bg_light_button이 바꾼다
        }

        for (key in keys) {
            val item = layoutInflater.inflate(R.layout.item_reservation, list, false)
            val t = LocalTime.parse(key)
            item.findViewById<TextView>(R.id.ampm).text = t.format(AMPM)
            item.findViewById<TextView>(R.id.time).text = t.format(CLOCK)
            // 한 번만 예약은 다음에 울릴 날짜. 꺼져 있으면 다시 켰을 때 울릴 날짜(알람 계산과 같은 규칙)
            val onceDate = MuteScheduler.nextTrigger(ZonedDateTime.now(), t, 0).toLocalDate()
            item.findViewById<TextView>(R.id.repeat).text = repeatLabel(MuteScheduler.daysOf(this, key), onceDate)
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
                visibility = if (sel == null) View.VISIBLE else View.GONE
                setOnCheckedChangeListener { _, on ->
                    showEnabled(on)
                    prefs.edit().putBoolean(key, on).apply()
                    MuteScheduler.apply(this@MainActivity)
                }
            }
            item.findViewById<CheckBox>(R.id.check).apply {
                visibility = if (sel == null) View.GONE else View.VISIBLE
                isChecked = sel?.contains(key) == true
            }
            item.setOnClickListener {
                if (sel == null) pickTime(key) else {
                    if (!sel.add(key)) sel.remove(key)
                    render()
                }
            }
            // 길게 누르면 선택 모드로, 누른 예약은 선택된 채로. 선택 모드에선 false라 일반 탭(토글)으로 처리된다
            item.setOnLongClickListener {
                if (sel != null) return@setOnLongClickListener false
                selection = mutableSetOf(key)
                render()
                true
            }
            list.addView(item)
        }
    }

    /** 요일 칸을 가로로 끌면 지나간 칸을 한꺼번에 켠다(시작 칸이 켜져 있었으면 끈다). 그냥 탭은 CheckBox가 처리 */
    private fun enableDragSelect(row: View, boxes: List<CheckBox>) {
        var from = -1
        var before = emptyList<Boolean>()
        var dragging = false
        val loc = IntArray(2)
        val listener = View.OnTouchListener { box, e ->
            row.getLocationOnScreen(loc)
            val at = ((e.rawX - loc[0]) / (row.width / boxes.size.toFloat())).toInt().coerceIn(0, boxes.lastIndex)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    from = at
                    before = boxes.map { it.isChecked }
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> if (!dragging && at != from) {
                    dragging = true
                    box.parent.requestDisallowInterceptTouchEvent(true)
                    // 눌림 상태를 풀어 손을 뗄 때 시작 칸이 한 번 더 토글되지 않게 한다
                    MotionEvent.obtain(e).also { it.action = MotionEvent.ACTION_CANCEL; box.onTouchEvent(it); it.recycle() }
                }
            }
            if (dragging) dragSelect(before, from, at).forEachIndexed { i, on -> boxes[i].isChecked = on }
            dragging // 드래그 중엔 이벤트를 먹어서 CheckBox의 탭 토글이 끼지 않게
        }
        boxes.forEach { it.setOnTouchListener(listener) }
    }

    /** 삭제 전 확인: 1개는 그 시각, 전부면 "전체", 그 외엔 개수로 묻는다. 시각·"전체"·"N개"는 키컬러로 강조 */
    private fun confirmDelete() {
        val sel = selection ?: return
        if (sel.isEmpty()) return
        val lime = ForegroundColorSpan(getColor(R.color.key))
        val span = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        val message: CharSequence = when {
            sel.size == 1 -> SpannableStringBuilder()
                .append(timeLabel(LocalTime.parse(sel.first())), lime, span).append(" 예약을 삭제하시겠습니까?")
            sel.size == prefs.all.size -> SpannableStringBuilder().append("전체", lime, span).append(" 예약을 삭제하시겠습니까?")
            else -> SpannableStringBuilder("예약 ").append("${sel.size}개", lime, span).append("를 삭제하시겠습니까?")
        }
        AlertDialog.Builder(this)
            .setMessage(message)
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                sel.forEach { MuteScheduler.delete(this, it) }
                exitSelection()
            }
            .showMessage()
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

        val t = old?.let(LocalTime::parse) ?: LocalTime.now() // 새 예약은 휴대폰 현재 시각으로 시작
        ampm.value = t.hour / 12
        hour.value = to12h(t.hour)
        minute.value = t.minute

        // 일~토 토글. 새 예약은 미선택(한 번만)으로 시작
        val daysRow = view.findViewById<ViewGroup>(R.id.days)
        val dayBoxes = (0 until 7).map { daysRow.getChildAt(it) as CheckBox }
        val oldDays = old?.let { MuteScheduler.daysOf(this, it) } ?: 0
        dayBoxes.forEachIndexed { i, box -> box.isChecked = oldDays and (1 shl i) != 0 }
        enableDragSelect(daysRow, dayBoxes)

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
                    .setMessage("${timeLabel(time)}에 이미 예약이 있어요.")
                    .setPositiveButton("확인", null)
                    .showMessage()
                return@setOnClickListener
            }
            val days = dayBoxes.foldIndexed(0) { i, acc, box -> if (box.isChecked) acc or (1 shl i) else acc }
            MuteScheduler.save(this, old, key, days, volume.progress)
            dialog.dismiss()
            render()
        }
    }
}
