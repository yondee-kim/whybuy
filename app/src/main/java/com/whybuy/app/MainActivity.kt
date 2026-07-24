package com.whybuy.app

import android.app.AppOpsManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.whybuy.app.service.AppWatchService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Week1Screen()
                }
            }
        }
    }
}

@Composable
fun Week1Screen() {
    val context = LocalContext.current
    var hasUsageAccess by remember { mutableStateOf(false) }

    // 화면 복귀 시마다 권한 재확인
    LaunchedEffect(Unit) {
        hasUsageAccess = checkUsageAccess(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("WHYBUY", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(8.dp))

        Text("Week 1 · 앱 감지 검증")

        Spacer(Modifier.height(32.dp))

        Text(
            if (hasUsageAccess) "사용 정보 접근: 허용됨"
            else "사용 정보 접근: 필요함"
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
        }) {
            Text("권한 설정 열기")
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            hasUsageAccess = checkUsageAccess(context)
        }) {
            Text("권한 다시 확인")
        }

        Spacer(Modifier.height(32.dp))

        Button(
            enabled = hasUsageAccess,
            onClick = {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AppWatchService::class.java)
                )
            }
        ) {
            Text("감지 시작")
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            context.stopService(Intent(context, AppWatchService::class.java))
        }) {
            Text("감지 중지")
        }

        Spacer(Modifier.height(24.dp))

        Text("Logcat에서 태그 WhyBuy 로 필터하세요")
    }
}

private fun checkUsageAccess(context: android.content.Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}