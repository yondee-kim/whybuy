# WHYBUY Android Architecture

Version 1.0

---

# 기술 스택

| 항목 | 선택 |
|---|---|
| 언어 | Kotlin |
| 최소 SDK | 26 (Android 8.0) |
| 목표 SDK | 35 |
| UI | Jetpack Compose |
| Overlay UI | Compose + ComposeView |
| DI | Hilt |
| 비동기 | Coroutines + Flow |
| 로컬 DB | Room |
| 설정 저장 | DataStore (Preferences) |
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization |
| 애니메이션 | Lottie |
| 이미지 | Coil |
| 백그라운드 | Foreground Service + WorkManager |
| 로그인 | Credential Manager (Google) |

---

## 최소 SDK를 26으로 잡은 이유

Foreground Service

Notification Channel

TYPE_APPLICATION_OVERLAY

이 세 가지가

API 26부터 안정적으로 동작한다.

---

# 아키텍처

## 구조

Clean Architecture + MVVM

---

```text
presentation
    ↓
domain
    ↓
data
```

---

## 의존 방향

presentation → domain ← data

domain은

어떤 프레임워크도 모른다.

---

# 모듈 구성

MVP는 단일 모듈로 시작한다.

패키지로 경계를 나눈다.

---

```text
com.whybuy.app

├── WhyBuyApplication.kt
│
├── di/
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   ├── NetworkModule.kt
│   └── ServiceModule.kt
│
├── core/
│   ├── ui/
│   │   ├── theme/
│   │   ├── component/
│   │   └── ext/
│   ├── common/
│   │   ├── Result.kt
│   │   ├── DispatcherProvider.kt
│   │   └── Clock.kt
│   └── permission/
│       ├── PermissionChecker.kt
│       └── PermissionIntentFactory.kt
│
├── domain/
│   ├── model/
│   │   ├── TargetApp.kt
│   │   ├── AppRule.kt
│   │   ├── Frequency.kt
│   │   ├── TimeWindow.kt
│   │   ├── DayRule.kt
│   │   ├── EncounterLog.kt
│   │   ├── Emotion.kt
│   │   └── Decision.kt
│   ├── repository/
│   │   ├── TargetAppRepository.kt
│   │   ├── RuleRepository.kt
│   │   ├── EncounterRepository.kt
│   │   └── SettingRepository.kt
│   └── usecase/
│       ├── EvaluateRuleUseCase.kt
│       ├── RecordEncounterUseCase.kt
│       ├── GetTodaySummaryUseCase.kt
│       ├── DowngradeFrequencyUseCase.kt
│       └── ToggleTargetAppUseCase.kt
│
├── data/
│   ├── local/
│   │   ├── WhyBuyDatabase.kt
│   │   ├── dao/
│   │   ├── entity/
│   │   └── converter/
│   ├── datastore/
│   │   └── SettingDataStore.kt
│   ├── remote/
│   │   ├── api/
│   │   ├── dto/
│   │   └── interceptor/
│   └── repository/
│       └── (impl)
│
├── service/
│   ├── AppWatchService.kt
│   ├── AppDetector.kt
│   ├── RuleEngine.kt
│   ├── OverlayController.kt
│   └── ServiceRestartReceiver.kt
│
├── overlay/
│   ├── OverlayHost.kt
│   ├── OverlayViewModel.kt
│   ├── OverlayStep.kt
│   └── ui/
│       ├── PyeonGreeting.kt
│       ├── PyeonEmotion.kt
│       └── PyeonDecision.kt
│
└── presentation/
    ├── MainActivity.kt
    ├── navigation/
    ├── splash/
    ├── login/
    ├── permission/
    ├── appselect/
    ├── rule/
    ├── home/
    ├── history/
    ├── setting/
    └── web/
```

---

# 핵심 1. 앱 실행 감지

## 방식 선택

| 방식 | 장점 | 단점 | 채택 |
|---|---|---|---|
| UsageStatsManager | 권한 1개, 안정적 | 폴링 필요 | 채택 |
| AccessibilityService | 즉시 감지 | Play 정책 심사 매우 엄격 | 제외 |
| ActivityManager | - | API 21부터 사실상 불가 | 제외 |

---

## AccessibilityService를 쓰지 않는 이유

접근성 서비스는

장애인 보조 목적 외 사용 시

Play Console 심사에서

거절되거나 삭제될 위험이 크다.

WHYBUY는

출시를 목표로 하므로

UsageStatsManager를 선택한다.

---

## UsageStatsManager 감지

```kotlin
class AppDetector @Inject constructor(
    private val usageStatsManager: UsageStatsManager,
    private val clock: Clock
) {
    private var lastEventTime = 0L

    fun currentForegroundPackage(): String? {
        val now = clock.now()
        val begin = if (lastEventTime == 0L) now - QUERY_WINDOW else lastEventTime

        val events = usageStatsManager.queryEvents(begin, now)
        val event = UsageEvents.Event()
        var latest: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latest = event.packageName
                lastEventTime = event.timeStamp
            }
        }
        return latest
    }

    companion object {
        private const val QUERY_WINDOW = 10_000L
    }
}
```

---

## 폴링 주기

| 상태 | 주기 |
|---|---|
| 화면 켜짐 | 1초 |
| 화면 꺼짐 | 폴링 중단 |
| 대상 앱 사용 중 | 3초 |

---

화면이 꺼지면

즉시 폴링을 멈춘다.

배터리는

WHYBUY의 생존 조건이다.

---

## 화면 상태 감지

```kotlin
class ScreenStateReceiver(
    private val onScreenOn: () -> Unit,
    private val onScreenOff: () -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> onScreenOn()
            Intent.ACTION_SCREEN_OFF -> onScreenOff()
        }
    }
}
```

동적 등록만 사용한다.

Manifest 등록은 동작하지 않는다.

---

# 핵심 2. Foreground Service

## AppWatchService

```kotlin
@AndroidEntryPoint
class AppWatchService : LifecycleService() {

    @Inject lateinit var detector: AppDetector
    @Inject lateinit var ruleEngine: RuleEngine
    @Inject lateinit var overlayController: OverlayController

    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTI_ID, buildNotification())
        registerScreenReceiver()
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                val pkg = detector.currentForegroundPackage()
                if (pkg != null && pkg != lastPackage) {
                    lastPackage = pkg
                    handleAppChanged(pkg)
                }
                delay(currentInterval())
            }
        }
    }

    private suspend fun handleAppChanged(pkg: String) {
        val result = ruleEngine.evaluate(pkg)
        if (result is RuleResult.Show) {
            overlayController.show(pkg)
        }
    }

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }
}
```

---

## Foreground Service Type

Android 14 이상은

타입 선언이 필수이다.

---

```xml
<service
    android:name=".service.AppWatchService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Detects when user opens a shopping app to show a
                       user-requested mindful pause overlay" />
</service>
```

---

specialUse는

Play Console 제출 시

사용 사유를 별도로 설명해야 한다.

이 문장은

14_Release_Checklist에도 동일하게 기재한다.

---

## 알림

낮은 중요도

Silent

---

```kotlin
NotificationChannel(
    CHANNEL_ID,
    "편이 대기 중",
    NotificationManager.IMPORTANCE_MIN
)
```

---

문구

편이가 조용히 기다리고 있어요.

---

## 서비스 재시작

| 상황 | 대응 |
|---|---|
| 부팅 완료 | BOOT_COMPLETED Receiver |
| 앱 업데이트 | MY_PACKAGE_REPLACED |
| 강제 종료 | WorkManager 주기 점검 (15분) |
| 배터리 최적화 | 예외 요청 안내 |

---

## 제조사 대응

삼성, 샤오미, 오포 등은

자체 절전 정책으로

서비스를 종료시킨다.

---

대응

설정 화면에

제조사별 안내 링크를 제공한다.

Don't kill my app 수준의

간단한 가이드를 내장한다.

---

# 핵심 3. RuleEngine

## 판정 순서

```kotlin
sealed interface RuleResult {
    data object Show : RuleResult
    data class Skip(val reason: SkipReason) : RuleResult
}

enum class SkipReason {
    NOT_TARGET, GLOBAL_PAUSED, APP_PAUSED,
    DAY_MISMATCH, TIME_MISMATCH,
    DAILY_LIMIT, MIN_INTERVAL, PERMISSION_MISSING
}
```

---

```kotlin
class RuleEngine @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val encounterRepository: EncounterRepository,
    private val settingRepository: SettingRepository,
    private val permissionChecker: PermissionChecker,
    private val clock: Clock
) {
    suspend fun evaluate(pkg: String): RuleResult {
        if (!permissionChecker.canDrawOverlay())
            return Skip(PERMISSION_MISSING)

        if (settingRepository.isGloballyPaused())
            return Skip(GLOBAL_PAUSED)

        val rule = ruleRepository.findByPackage(pkg)
            ?: return Skip(NOT_TARGET)

        if (rule.isPausedAt(clock.now()))
            return Skip(APP_PAUSED)

        if (!rule.dayRule.matches(clock.today()))
            return Skip(DAY_MISMATCH)

        if (!rule.timeWindow.contains(clock.localTime()))
            return Skip(TIME_MISMATCH)

        val state = encounterRepository.stateOf(pkg)

        if (!rule.frequency.allows(state, clock.now()))
            return Skip(DAILY_LIMIT)

        return Show
    }
}
```

---

## Frequency 판정

```kotlin
sealed interface Frequency {
    data object EveryTime : Frequency
    data object OnceADay : Frequency
    data class MaxPerDay(val count: Int) : Frequency
    data object OnceAWeek : Frequency
    data class MinInterval(val minutes: Int) : Frequency
}
```

---

## TimeWindow 자정 처리

```kotlin
data class TimeWindow(
    val type: WindowType,
    val start: LocalTime,
    val end: LocalTime
) {
    fun contains(now: LocalTime): Boolean {
        val inRange = if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
        return when (type) {
            ALWAYS -> true
            ONLY_BETWEEN -> inRange
            EXCEPT_BETWEEN -> !inRange
        }
    }
}
```

---

22:00 ~ 03:00 같은

자정을 넘는 구간을

반드시 지원해야 한다.

밤 늦은 소비가

WHYBUY의 핵심 시나리오이기 때문이다.

---

## 하루 기준 시각

하루의 시작은

00:00이 아니라

새벽 4시로 정의한다.

---

이유

새벽 2시의 쇼핑은

전날의 연장이다.

사용자의 체감과

시스템의 날짜를 맞춘다.

---

# 핵심 4. Overlay

## WindowManager 설정

```kotlin
class OverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingRepository: SettingRepository
) {
    private val windowManager =
        context.getSystemService(WindowManager::class.java)

    private var overlayView: View? = null

    fun show(pkg: String) {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WRAP_CONTENT,
            WRAP_CONTENT,
            TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_FOCUSABLE or
                FLAG_LAYOUT_NO_LIMITS or
                FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24.dp
            y = 96.dp
        }

        val view = createComposeView(pkg)
        overlayView = view
        windowManager.addView(view, params)
    }

    fun dismiss() {
        overlayView?.let {
            animateOut(it) {
                runCatching { windowManager.removeView(it) }
                overlayView = null
            }
        }
    }
}
```

---

## 플래그 선택 이유

| 플래그 | 이유 |
|---|---|
| FLAG_NOT_FOCUSABLE | 뒤쪽 앱의 키보드 입력을 뺏지 않는다 |
| FLAG_LAYOUT_NO_LIMITS | 제스처 영역까지 자연스럽게 배치 |
| FLAG_WATCH_OUTSIDE_TOUCH | 바깥 터치 시 조용히 물러난다 |

---

FLAG_NOT_TOUCH_MODAL은

기본 동작에 포함되므로

명시하지 않는다.

---

## Compose를 Overlay에 붙이기

Compose는

ViewTreeLifecycleOwner를 요구한다.

Service에는 기본적으로 없다.

---

```kotlin
class OverlayLifecycleOwner :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle get() = lifecycleRegistry
    override val viewModelStore get() = store
    override val savedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
```

---

```kotlin
private fun createComposeView(pkg: String): View {
    val owner = OverlayLifecycleOwner().apply { onCreate() }

    return ComposeView(context).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent {
            WhyBuyTheme {
                OverlayHost(
                    packageName = pkg,
                    onFinish = { dismiss() }
                )
            }
        }
    }
}
```

---

이 부분이

Overlay 구현에서 가장 자주 막히는 지점이다.

LifecycleOwner를 붙이지 않으면

ComposeView는

아무것도 그리지 않고 조용히 실패한다.

---

## 드래그 이동

```kotlin
view.setOnTouchListener { _, event ->
    when (event.action) {
        ACTION_DOWN -> {
            initialX = params.x
            initialY = params.y
            touchX = event.rawX
            touchY = event.rawY
            true
        }
        ACTION_MOVE -> {
            params.x = initialX - (event.rawX - touchX).toInt()
            params.y = initialY - (event.rawY - touchY).toInt()
            windowManager.updateViewLayout(view, params)
            true
        }
        ACTION_UP -> {
            savePosition(params.x, params.y)
            false
        }
        else -> false
    }
}
```

gravity가 BOTTOM|END이므로

x, y 부호가 반대인 점에 주의한다.

---

## 등장 애니메이션

```kotlin
val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(
        durationMillis = 1000,
        easing = FastOutSlowInEasing
    )
)
```

---

Spring 사용 시

dampingRatio는

DampingRatioNoBouncy만 허용한다.

편이는 튀지 않는다.

---

## Overlay 상태 기계

```kotlin
sealed interface OverlayStep {
    data object Greeting : OverlayStep
    data object Emotion : OverlayStep
    data object Decision : OverlayStep
    data object Farewell : OverlayStep
    data object CoolDownAsk : OverlayStep
}
```

---

## 타임아웃

Greeting 단계

8초 무응답

↓

자동 종료

---

Emotion, Decision 단계

30초 무응답

↓

자동 종료

---

기록은 남긴다.

DISMISSED_TIMEOUT

---

## 강제 종료 트리거

- 화면 꺼짐
- 대상 앱이 백그라운드로 이동
- 전화 수신
- 다른 Overlay 앱과 충돌

---

# 핵심 5. 권한

## PermissionChecker

```kotlin
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun canDrawOverlay(): Boolean =
        Settings.canDrawOverlays(context)

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
```

---

## 권한 이동 Intent

```kotlin
object PermissionIntentFactory {
    fun overlay(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun usageAccess() =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun batteryOptimization(context: Context) = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
}
```

---

## 확인 방식

권한은

콜백으로 알 수 없다.

Activity onResume에서

다시 확인한다.

---

## 권한 없을 때

앱은 정상 동작한다.

편이만 나타나지 않는다.

---

크래시 금지

강제 이동 금지

반복 팝업 금지

---

# 핵심 6. Room

## Database

```kotlin
@Database(
    entities = [
        TargetAppEntity::class,
        AppRuleEntity::class,
        EncounterLogEntity::class,
        EncounterStateEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(WhyBuyConverters::class)
abstract class WhyBuyDatabase : RoomDatabase() {
    abstract fun targetAppDao(): TargetAppDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun encounterDao(): EncounterDao
}
```

---

## 주의

Overlay는

Service 컨텍스트에서 동작한다.

DB 접근은

반드시 Dispatchers.IO에서 수행한다.

---

## 쿼리 예시

```kotlin
@Dao
interface EncounterDao {

    @Query("""
        SELECT COUNT(*) FROM encounter_log
        WHERE packageName = :pkg
          AND serviceDate = :date
          AND shown = 1
    """)
    suspend fun countToday(pkg: String, date: String): Int

    @Query("""
        SELECT * FROM encounter_log
        ORDER BY createdAt DESC
    """)
    fun observeAll(): PagingSource<Int, EncounterLogEntity>
}
```

---

serviceDate는

새벽 4시 기준으로 계산된 문자열이다.

---

# 핵심 7. DataStore

Room에 넣지 않는 값

---

- 로그인 토큰 (암호화)
- 전체 멈춤 여부
- 편이 크기
- 편이 위치
- 알림 표시 여부
- 온보딩 완료 여부

---

토큰은

EncryptedSharedPreferences 또는

DataStore + 자체 암호화로 저장한다.

---

# 핵심 8. 네트워크

MVP에서 서버는

로그인과 백업에만 쓴다.

---

```kotlin
interface AuthApi {
    @POST("/api/v1/auth/google")
    suspend fun loginWithGoogle(
        @Body request: GoogleLoginRequest
    ): ApiResponse<TokenResponse>

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): ApiResponse<TokenResponse>
}

interface BackupApi {
    @POST("/api/v1/backup")
    suspend fun upload(
        @Body request: BackupRequest
    ): ApiResponse<BackupMeta>

    @GET("/api/v1/backup/latest")
    suspend fun latest(): ApiResponse<BackupResponse>
}
```

---

## 오프라인 우선

네트워크 실패는

기능 실패가 아니다.

모든 화면은

로컬 데이터만으로 동작해야 한다.

---

# 성능 목표

| 항목 | 목표 |
|---|---|
| 편이 등장 지연 | 앱 실행 후 2초 이내 |
| 하루 배터리 소모 | 2% 이하 |
| Overlay 메모리 | 30MB 이하 |
| APK 크기 | 20MB 이하 |
| Cold Start | 1.5초 이내 |

---

# 배터리 전략

1. 화면 꺼짐 시 폴링 완전 중단
2. 대상 앱 아닐 때 즉시 return
3. 감지된 패키지가 이전과 같으면 DB 조회 생략
4. RuleEngine 판정 결과를 짧게 캐싱
5. Lottie는 Overlay 표시 중에만 로드

---

# 테스트

## Unit

- RuleEngine 전체 분기
- TimeWindow 자정 처리
- Frequency 카운트
- serviceDate 계산

---

## Instrumented

- Room 마이그레이션
- DAO 쿼리

---

## Manual

- 제조사별 절전 대응
- 다른 Overlay 앱과 충돌
- 분할 화면
- 폴더블 전환
- 다크모드
- 폰트 확대

---

# 빌드

## Variant

- debug
- release

---

## 서명

Play App Signing 사용

---

## 난독화

R8 활성

Room, Retrofit, Lottie

keep 규칙 확인

---

# 알려진 위험

| 위험 | 대응 |
|---|---|
| specialUse FGS 심사 거절 | 사유 문서 상세 작성, 데모 영상 제출 |
| 제조사 절전으로 서비스 종료 | 안내 가이드 + WorkManager 재기동 |
| Overlay 권한 거부율 | 온보딩 문구 개선, 거부해도 사용 가능 |
| UsageStats 폴링 지연 | 주기 조정, 등장 지연 허용 |
| Compose Overlay 렌더 실패 | LifecycleOwner 처리 필수 |
