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
import com.whybuy.app.core.ServiceDate
import com.whybuy.app.data.WhyBuyDatabase
import com.whybuy.app.data.entity.AppRuleEntity
import com.whybuy.app.data.entity.TargetAppEntity
import com.whybuy.app.domain.RuleResult
import com.whybuy.app.MainActivity
import com.whybuy.app.R
import com.whybuy.app.WhyBuyApplication
import com.whybuy.app.overlay.EndReason
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppWatchService : LifecycleService() {

    private lateinit var detector: AppDetector
    private lateinit var overlayController: OverlayController
    private var pollingJob: Job? = null
    private var lastPackage: String? = null
    private lateinit var db: WhyBuyDatabase
    private lateinit var ruleEngine: RuleEngine

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
        db = WhyBuyDatabase.get(this)
        ruleEngine = RuleEngine(db)
        seedTestDataIfEmpty()
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

        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                ruleEngine.evaluate(packageName, now)
            }

            when (result) {
                is RuleResult.Show -> {
                    withContext(Dispatchers.IO) {
                        ruleEngine.recordShown(packageName, now)
                    }
                    overlayController.show(packageName)
                }
                is RuleResult.Skip -> {
                    Log.d(TAG, "편이 건너뜀 · ${result.reason}")
                    overlayController.dismiss(EndReason.APP_CLOSED)
                }
            }
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
    }

    /** 앱 선택 화면이 없으므로 임시로 대상 앱을 넣어둔다 */
    private fun seedTestDataIfEmpty() {
        lifecycleScope.launch(Dispatchers.IO) {
            val existing = db.targetAppDao().getEnabled()
            if (existing.isNotEmpty()) return@launch

            val pkg = "com.coupang.mobile"

            db.targetAppDao().upsert(
                TargetAppEntity(packageName = pkg, appName = "쿠팡")
            )
            db.appRuleDao().upsert(
                AppRuleEntity(
                    packageName = pkg,
                    frequencyType = "MIN_INTERVAL",
                    frequencyValue = 1        // 테스트용 1분 간격

                    /*
                    // 하루 한 번
                    frequencyType = "ONCE_A_DAY"

                    // 밤 10시 이후만
                    frequencyType = "EVERY_TIME",
                    windowType = "ONLY_BETWEEN",
                    startTime = 22 * 60,   // 1320
                    endTime = 3 * 60       // 180

                    // 주말만
                    dayRuleType = "WEEKEND"
                     */
                )
            )
            Log.d(TAG, "테스트 데이터 시드 완료")
        }
    }
}