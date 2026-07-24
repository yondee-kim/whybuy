package com.whybuy.app.domain

import com.whybuy.app.data.entity.AppRuleEntity
import com.whybuy.app.data.entity.EncounterStateEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

// ---------- 빈도 ----------

sealed interface Frequency {
    data object EveryTime : Frequency
    data object OnceADay : Frequency
    data class MaxPerDay(val count: Int) : Frequency
    data object OnceAWeek : Frequency
    data class MinInterval(val minutes: Int) : Frequency

    fun allows(state: EncounterStateEntity?, now: Long, today: String, week: String): Boolean {
        if (state == null) return true

        return when (this) {
            EveryTime -> true

            OnceADay ->
                state.todayDate != today || state.todayCount < 1

            is MaxPerDay ->
                state.todayDate != today || state.todayCount < count

            OnceAWeek ->
                state.weekKey != week || state.weekCount < 1

            is MinInterval ->
                now - state.lastShownAt >= minutes * 60_000L
        }
    }

    companion object {
        fun from(type: String, value: Int?): Frequency = when (type) {
            "EVERY_TIME" -> EveryTime
            "ONCE_A_DAY" -> OnceADay
            "MAX_PER_DAY" -> MaxPerDay(value ?: 1)
            "ONCE_A_WEEK" -> OnceAWeek
            "MIN_INTERVAL" -> MinInterval(value ?: 60)
            else -> OnceADay
        }
    }
}

// ---------- 시간대 ----------

enum class WindowType { ALWAYS, ONLY_BETWEEN, EXCEPT_BETWEEN }

data class TimeWindow(
    val type: WindowType,
    val startMinute: Int?,
    val endMinute: Int?
) {
    fun contains(now: LocalTime): Boolean {
        if (type == WindowType.ALWAYS) return true
        val start = startMinute ?: return true
        val end = endMinute ?: return true

        val current = now.hour * 60 + now.minute

        // 자정을 넘는 구간 지원 (예: 22:00 ~ 03:00)
        val inRange = if (start <= end) {
            current >= start && current < end
        } else {
            current >= start || current < end
        }

        return when (type) {
            WindowType.ONLY_BETWEEN -> inRange
            WindowType.EXCEPT_BETWEEN -> !inRange
            WindowType.ALWAYS -> true
        }
    }

    companion object {
        fun from(type: String, start: Int?, end: Int?) = TimeWindow(
            type = runCatching { WindowType.valueOf(type) }
                .getOrDefault(WindowType.ALWAYS),
            startMinute = start,
            endMinute = end
        )
    }
}

// ---------- 요일 ----------

enum class DayRuleType { EVERYDAY, WEEKDAY, WEEKEND, CUSTOM }

data class DayRule(
    val type: DayRuleType,
    val customDays: Int?
) {
    fun matches(day: DayOfWeek): Boolean = when (type) {
        DayRuleType.EVERYDAY -> true
        DayRuleType.WEEKDAY -> day.value in 1..5
        DayRuleType.WEEKEND -> day.value in 6..7
        DayRuleType.CUSTOM -> {
            val mask = customDays ?: 0
            val bit = 1 shl (day.value - 1)
            mask and bit != 0
        }
    }

    companion object {
        fun from(type: String, custom: Int?) = DayRule(
            type = runCatching { DayRuleType.valueOf(type) }
                .getOrDefault(DayRuleType.EVERYDAY),
            customDays = custom
        )
    }
}

// ---------- 통합 규칙 ----------

data class AppRule(
    val packageName: String,
    val frequency: Frequency,
    val timeWindow: TimeWindow,
    val dayRule: DayRule,
    val pausedUntil: Long?
) {
    fun isPausedAt(now: Long): Boolean =
        pausedUntil != null && now < pausedUntil

    companion object {
        fun from(entity: AppRuleEntity) = AppRule(
            packageName = entity.packageName,
            frequency = Frequency.from(entity.frequencyType, entity.frequencyValue),
            timeWindow = TimeWindow.from(
                entity.windowType, entity.startTime, entity.endTime
            ),
            dayRule = DayRule.from(entity.dayRuleType, entity.customDays),
            pausedUntil = entity.pausedUntil
        )
    }
}

// ---------- 판정 결과 ----------

sealed interface RuleResult {
    data object Show : RuleResult
    data class Skip(val reason: SkipReason) : RuleResult
}

enum class SkipReason {
    NOT_TARGET,
    APP_PAUSED,
    DAY_MISMATCH,
    TIME_MISMATCH,
    FREQUENCY_LIMIT
}

// ---------- 시간 유틸 ----------

fun localTimeOf(millis: Long): LocalTime =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()

fun dayOfWeekOf(millis: Long): DayOfWeek =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .minusHours(4)   // 새벽 4시 기준
        .dayOfWeek