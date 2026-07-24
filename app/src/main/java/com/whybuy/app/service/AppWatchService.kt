package com.whybuy.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.whybuy.app.MainActivity
import com.whybuy.app.R
import com.whybuy.app.WhyBuyApplication
import com.whybuy.app.overlay.EndReason
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppWatchService : LifecycleService() {

    private lateinit var detector: AppDetector
    private lateinit var overlayController: OverlayController
    private var pollingJob: Job? = null
    private var lastPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "화면 켜짐 - 폴링 시작")
                    startPolling()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "화면 꺼짐 - 폴링 중단")
                    stopPolling()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        detector = AppDetector(this)
        overlayController = OverlayController(this)
        startForeground(NOTI_ID, buildNotification())

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )

        startPolling()
        Log.d(TAG, "서비스 시작")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = lifecycleScope.launch {
            while (isActive) {
                val pkg = detector.currentForegroundPackage()
                if (pkg != null && pkg != lastPackage) {
                    lastPackage = pkg
                    onAppChanged(pkg)
                }
                delay(POLL_INTERVAL)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun onAppChanged(packageName: String) {
        Log.d(TAG, "앱 전환 감지 >>> $packageName")

        if (packageName in TARGET_APPS) {
            overlayController.show(packageName)
        } else {
            overlayController.dismiss(EndReason.APP_CLOSED)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WhyBuyApplication.CHANNEL_ID)
            .setContentTitle("편이가 기다리고 있어요")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopPolling()
        overlayController.dismiss(EndReason.APP_CLOSED)
        unregisterReceiver(screenReceiver)
        Log.d(TAG, "서비스 종료")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WhyBuy"
        private const val NOTI_ID = 1001
        private const val POLL_INTERVAL = 1000L
        private val TARGET_APPS = setOf(
            "com.coupang.mobile" // 쿠팡
        )
    }
}