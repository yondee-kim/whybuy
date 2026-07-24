package com.whybuy.app.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

object ServiceDate {

    private const val DAY_START_HOUR = 4L

    /** 새벽 4시 기준 날짜. 새벽 2시는 전날로 계산된다. */
    fun of(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .minusHours(DAY_START_HOUR)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /** yyyy-ww 형식 주차 키 */
    fun weekKey(millis: Long): String {
        val date = Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .minusHours(DAY_START_HOUR)
            .toLocalDate()

        val week = date.get(WeekFields.of(Locale.KOREA).weekOfWeekBasedYear())
        val year = date.get(WeekFields.of(Locale.KOREA).weekBasedYear())
        return String.format(Locale.KOREA, "%d-%02d", year, week)
    }

    fun today(): String = of(System.currentTimeMillis())
}