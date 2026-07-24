package com.whybuy.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class WhyBuyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "편이 대기 중",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "편이가 조용히 기다리고 있어요."
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "whybuy_watch"
    }
}