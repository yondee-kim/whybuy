package com.whybuy.app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class AppDetector(context: Context) {

    private val usageStatsManager =
        context.getSystemService(UsageStatsManager::class.java)

    private var lastEventTime = 0L

    /**
     * 마지막 조회 이후 새로 전환된 앱의 패키지명.
     * 변화가 없으면 null.
     */
    fun currentForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        val begin = if (lastEventTime == 0L) now - QUERY_WINDOW else lastEventTime

        val events = usageStatsManager.queryEvents(begin, now)
        val event = UsageEvents.Event()
        var latest: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latest = event.packageName
                lastEventTime = event.timeStamp + 1
            }
        }
        return latest
    }

    companion object {
        private const val QUERY_WINDOW = 10_000L
    }
}