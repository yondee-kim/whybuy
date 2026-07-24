# WHYBUY Database

Version 1.0

---

# 설계 원칙

## 1. 로컬이 원본이다

WHYBUY의 모든 데이터는

사용자 기기에 저장된다.

서버는

원본이 아니라

사본 보관소이다.

---

## 2. 최소 수집

수집하지 않는 것

- 구매 금액
- 상품명
- 검색어
- 장바구니 내용
- 화면 캡처
- 위치

---

기술적으로 가능해도

수집하지 않는다.

---

## 3. 감정은 코드로 저장한다

감정을 문장으로 저장하면

언젠가 분석하고 싶어진다.

그래서

코드로만 저장한다.

---

## 4. 서버는 내용을 모른다

백업은

앱이 만든 JSON 덩어리이다.

서버는 파싱하지 않는다.

---

# 로컬 DB (Room)

## ERD

```text
┌──────────────────┐
│   target_app     │
│──────────────────│
│ packageName  PK  │
│ appName          │
│ category         │
│ enabled          │
│ addedAt          │
└────────┬─────────┘
         │ 1
         │
         │ 1
┌────────┴─────────┐
│    app_rule      │
│──────────────────│
│ packageName  PK/FK│
│ frequencyType    │
│ frequencyValue   │
│ windowType       │
│ startTime        │
│ endTime          │
│ dayRuleType      │
│ customDays       │
│ pausedUntil      │
│ updatedAt        │
└────────┬─────────┘
         │ 1
         │
         │ 1
┌────────┴─────────┐
│ encounter_state  │
│──────────────────│
│ packageName  PK/FK│
│ lastShownAt      │
│ todayCount       │
│ todayDate        │
│ weekCount        │
│ weekKey          │
│ consecutiveSkip  │
└──────────────────┘

┌──────────────────┐
│  encounter_log   │
│──────────────────│
│ id           PK  │
│ packageName      │
│ appName          │
│ shownAt          │
│ serviceDate      │
│ emotionCode      │
│ emotionMemo      │
│ decisionCode     │
│ endReason        │
│ durationMs       │
│ createdAt        │
└──────────────────┘

┌──────────────────┐
│  DataStore       │
│  (설정값)         │
└──────────────────┘
```

---

## target_app

감시 대상 앱

---

| 컬럼 | 타입 | 설명 |
|---|---|---|
| packageName | TEXT PK | 패키지명 |
| appName | TEXT | 표시 이름 |
| category | TEXT | SHOPPING / DELIVERY / SNS / GAME / ETC |
| enabled | INTEGER | 사용 여부 |
| addedAt | INTEGER | 추가 시각 |

---

```kotlin
@Entity(tableName = "target_app")
data class TargetAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,
    val enabled: Boolean = true,
    val addedAt: Long
)
```

---

## app_rule

앱별 만남 규칙

target_app과 1:1

---

| 컬럼 | 타입 | 설명 |
|---|---|---|
| packageName | TEXT PK FK | 대상 앱 |
| frequencyType | TEXT | 빈도 유형 |
| frequencyValue | INTEGER | N번 / N분 |
| windowType | TEXT | ALWAYS / ONLY_BETWEEN / EXCEPT_BETWEEN |
| startTime | INTEGER | 분 단위 (0~1439) |
| endTime | INTEGER | 분 단위 |
| dayRuleType | TEXT | EVERYDAY / WEEKDAY / WEEKEND / CUSTOM |
| customDays | INTEGER | 비트마스크 |
| pausedUntil | INTEGER | 이 시각까지 멈춤, null이면 활성 |
| updatedAt | INTEGER | 수정 시각 |

---

### frequencyType

| 값 | frequencyValue |
|---|---|
| EVERY_TIME | 미사용 |
| ONCE_A_DAY | 미사용 |
| MAX_PER_DAY | 횟수 |
| ONCE_A_WEEK | 미사용 |
| MIN_INTERVAL | 분 |

---

### customDays 비트마스크

| 요일 | 비트 |
|---|---|
| 월 | 1 |
| 화 | 2 |
| 수 | 4 |
| 목 | 8 |
| 금 | 16 |
| 토 | 32 |
| 일 | 64 |

---

주말만 = 32 + 64 = 96

---

### 시간 저장 방식

LocalTime을 그대로 저장하지 않고

자정 기준 분 단위 정수로 저장한다.

---

22:00 → 1320

03:00 → 180

---

startTime > endTime이면

자정을 넘는 구간이다.

---

```kotlin
@Entity(
    tableName = "app_rule",
    foreignKeys = [ForeignKey(
        entity = TargetAppEntity::class,
        parentColumns = ["packageName"],
        childColumns = ["packageName"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val frequencyType: String = "ONCE_A_DAY",
    val frequencyValue: Int? = null,
    val windowType: String = "ALWAYS",
    val startTime: Int? = null,
    val endTime: Int? = null,
    val dayRuleType: String = "EVERYDAY",
    val customDays: Int? = null,
    val pausedUntil: Long? = null,
    val updatedAt: Long
)
```

---

## encounter_state

규칙 판정용 카운터

기기에만 존재한다.

---

| 컬럼 | 타입 | 설명 |
|---|---|---|
| packageName | TEXT PK FK | 대상 앱 |
| lastShownAt | INTEGER | 마지막 노출 시각 |
| todayCount | INTEGER | 오늘 노출 횟수 |
| todayDate | TEXT | 기준 날짜 (yyyy-MM-dd) |
| weekCount | INTEGER | 이번 주 노출 횟수 |
| weekKey | TEXT | yyyy-ww |
| consecutiveSkip | INTEGER | 연속 거절 횟수 |

---

### 왜 카운터를 따로 두는가

encounter_log를 COUNT 하면

정확하지만 느리다.

Overlay 판정은

앱 실행 직후 200ms 안에 끝나야 한다.

그래서

카운터를 별도로 유지한다.

---

### todayDate

새벽 4시 기준으로 계산한다.

---

```kotlin
fun serviceDate(now: Long): String {
    val dt = Instant.ofEpochMilli(now)
        .atZone(ZoneId.systemDefault())
        .minusHours(4)
    return dt.toLocalDate().toString()
}
```

---

새벽 2시의 쇼핑은

전날로 기록된다.

시스템 날짜가 아니라

사용자의 하루를 따른다.

---

## encounter_log

편이와의 만남 기록

---

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | INTEGER PK AUTO | |
| packageName | TEXT | 대상 앱 |
| appName | TEXT | 표시용 (앱 삭제 대비) |
| shownAt | INTEGER | 등장 시각 |
| serviceDate | TEXT | 기준 날짜 |
| emotionCode | TEXT | 감정 코드, null 가능 |
| emotionMemo | TEXT | 기타 입력, 최대 20자 |
| decisionCode | TEXT | 선택 결과, null 가능 |
| endReason | TEXT | 종료 사유 |
| durationMs | INTEGER | 머문 시간 |
| createdAt | INTEGER | 저장 시각 |

---

### emotionCode

| 코드 | 문구 |
|---|---|
| TIRED | 조금 지쳤어요. |
| BROWSING | 그냥 구경 중이에요. |
| REWARD | 나에게 선물하고 싶어요. |
| NEEDED | 정말 필요한 것 같아요. |
| UNKNOWN | 잘 모르겠어요. |
| ETC | 기타 |

---

문구는 DB에 저장하지 않는다.

코드만 저장한다.

문구가 바뀌어도

과거 기록은 그대로 유지된다.

---

### decisionCode

| 코드 | 문구 |
|---|---|
| BUY_NOW | 지금 살게요. |
| THINK_MORE | 조금 더 볼게요. |
| LATER | 다음에 올게요. |

---

### endReason

| 코드 | 설명 |
|---|---|
| COMPLETED | 끝까지 진행 |
| DECLINED | 괜찮아요 선택 |
| DISMISSED_TIMEOUT | 무응답 |
| DISMISSED_OUTSIDE | 바깥 터치 |
| APP_CLOSED | 대상 앱 종료 |
| SCREEN_OFF | 화면 꺼짐 |

---

### 인덱스

```sql
CREATE INDEX idx_log_date ON encounter_log(serviceDate);
CREATE INDEX idx_log_pkg ON encounter_log(packageName, shownAt);
```

---

### 보관

기본

무제한

---

사용자가 원하면

- 개별 삭제
- 날짜 범위 삭제
- 전체 삭제

---

자동 삭제는 하지 않는다.

기록을 지우는 것은

사용자의 결정이다.

---

## DataStore 저장 항목

Room에 넣지 않는 값

---

| 키 | 타입 | 기본값 |
|---|---|---|
| onboarding_completed | Boolean | false |
| global_paused_until | Long | null |
| pyeon_size | String | MEDIUM |
| pyeon_position_x | Int | 24 |
| pyeon_position_y | Int | 96 |
| pyeon_side | String | END |
| vibration_enabled | Boolean | false |
| sound_enabled | Boolean | false |
| notification_visible | Boolean | true |
| backup_enabled | Boolean | false |
| last_backup_at | Long | null |
| access_token | String | null |
| refresh_token | String | null |

---

토큰은 암호화하여 저장한다.

---

# 서버 DB (MySQL)

## ERD

```text
┌──────────────────┐
│      users       │
│──────────────────│
│ id           PK  │
│ provider         │
│ provider_id      │
│ email            │
│ nickname         │
│ status           │
│ created_at       │
│ updated_at       │
│ deleted_at       │
└───┬──────────┬───┘
    │ 1        │ 1
    │          │
    │ N        │ 0..1
┌───┴──────┐ ┌─┴────────────────┐
│ refresh  │ │ backup_snapshots │
│ _tokens  │ │──────────────────│
│──────────│ │ id           PK  │
│ id   PK  │ │ user_id      FK  │
│ user_id  │ │ schema_version   │
│ token_   │ │ storage_type     │
│  hash    │ │ payload (JSON)   │
│ expires_ │ │ s3_key           │
│  at      │ │ size_bytes       │
│ revoked  │ │ device_hash      │
│ created_ │ │ created_at       │
│  at      │ │ updated_at       │
└──────────┘ └──────────────────┘

┌──────────────────┐   ┌──────────────────┐
│     notices      │   │    app_meta      │
│──────────────────│   │──────────────────│
│ id           PK  │   │ id           PK  │
│ title            │   │ package_name  UK │
│ content          │   │ display_name     │
│ type             │   │ category         │
│ pinned           │   │ recommended_freq │
│ published_at     │   │ icon_url         │
│ created_at       │   │ active           │
└──────────────────┘   │ sort_order       │
                       └──────────────────┘
```

---

## users

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| provider | VARCHAR(20) | GOOGLE / KAKAO / NAVER / APPLE |
| provider_id | VARCHAR(255) | 소셜 고유 ID |
| email | VARCHAR(255) | 연락용, 응답에 미포함 |
| nickname | VARCHAR(50) | 자동 생성 |
| status | VARCHAR(20) | ACTIVE / WITHDRAWN |
| created_at | DATETIME | |
| updated_at | DATETIME | |
| deleted_at | DATETIME | 탈퇴 시각 |

---

```sql
CREATE UNIQUE INDEX uk_users_provider
  ON users(provider, provider_id);
```

---

### 탈퇴 처리

1. status = WITHDRAWN
2. email, provider_id 익명화
3. backup_snapshots 즉시 삭제
4. refresh_tokens 즉시 삭제
5. 30일 후 users 행 삭제

---

익명화 방식

email → withdrawn_{id}@whybuy.local

provider_id → withdrawn_{id}

---

## refresh_tokens

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK | |
| token_hash | VARCHAR(255) | SHA-256 |
| expires_at | DATETIME | |
| revoked | BOOLEAN | |
| created_at | DATETIME | |

---

원문 토큰은 저장하지 않는다.

---

## backup_snapshots

사용자당 1행

---

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT FK UK | 유일 |
| schema_version | INT | 앱 스키마 버전 |
| storage_type | VARCHAR(10) | DB / S3 |
| payload | JSON | 256KB 미만일 때 |
| s3_key | VARCHAR(255) | 256KB 이상일 때 |
| size_bytes | INT | |
| device_hash | VARCHAR(64) | 기기 식별 해시 |
| created_at | DATETIME | |
| updated_at | DATETIME | |

---

### payload 구조

서버는 이 구조를 해석하지 않는다.

앱만 안다.

---

```json
{
  "schemaVersion": 1,
  "exportedAt": 1753350000000,
  "targetApps": [
    {
      "packageName": "com.coupang.mobile",
      "appName": "쿠팡",
      "category": "SHOPPING",
      "enabled": true,
      "addedAt": 1753000000000
    }
  ],
  "rules": [
    {
      "packageName": "com.coupang.mobile",
      "frequencyType": "ONCE_A_DAY",
      "frequencyValue": null,
      "windowType": "ALWAYS",
      "startTime": null,
      "endTime": null,
      "dayRuleType": "EVERYDAY",
      "customDays": null
    }
  ],
  "encounters": [
    {
      "packageName": "com.coupang.mobile",
      "shownAt": 1753350000000,
      "serviceDate": "2026-07-24",
      "emotionCode": "TIRED",
      "emotionMemo": null,
      "decisionCode": "THINK_MORE",
      "endReason": "COMPLETED"
    }
  ]
}
```

---

### 백업에 포함하지 않는 것

- encounter_state (기기별 카운터)
- 토큰
- 편이 위치

---

기기가 바뀌면

카운터는 새로 시작한다.

---

## app_meta

추천 앱 목록

앱 업데이트 없이 갱신 가능

---

| 컬럼 | 타입 |
|---|---|
| id | BIGINT PK |
| package_name | VARCHAR(255) UK |
| display_name | VARCHAR(100) |
| category | VARCHAR(20) |
| recommended_freq | VARCHAR(20) |
| icon_url | VARCHAR(500) |
| active | BOOLEAN |
| sort_order | INT |

---

## notices

| 컬럼 | 타입 |
|---|---|
| id | BIGINT PK |
| title | VARCHAR(200) |
| content | TEXT |
| type | VARCHAR(20) |
| pinned | BOOLEAN |
| published_at | DATETIME |
| created_at | DATETIME |

---

# 마이그레이션

## 로컬 (Room)

exportSchema = true

스키마 JSON을 git에 커밋한다.

---

파괴적 마이그레이션 금지

fallbackToDestructiveMigration을

절대 사용하지 않는다.

---

사용자의 기록은

다시 만들 수 없다.

---

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE encounter_log ADD COLUMN durationMs INTEGER"
        )
    }
}
```

---

## 서버 (Flyway)

```text
src/main/resources/db/migration/
├── V1__init.sql
├── V2__add_backup_snapshot.sql
└── V3__add_app_meta.sql
```

---

# 데이터 흐름 요약

```text
편이 등장
    ↓
사용자 선택
    ↓
encounter_log INSERT
encounter_state UPDATE
    ↓
(로컬 완료)

    ↓ 사용자가 백업을 켰을 때만

앱이 JSON 생성
    ↓
POST /api/v1/backup
    ↓
backup_snapshots UPSERT
```

---

# 용량 예상

## 로컬

하루 3회 만남 기준

기록 1건 약 200바이트

---

1년

3 × 365 × 200B ≈ 220KB

---

무시할 수 있는 크기이다.

---

## 서버

사용자 1만 명 기준

백업 평균 50KB

---

총 500MB

---

RDS 20GB로 충분하다.

---

# 마지막 원칙

이 DB에는

사용자가 무엇을 샀는지 없다.

얼마를 썼는지도 없다.

어떤 상품을 봤는지도 없다.

---

여기 남는 것은

그날 어떤 마음이었는지

사용자가 스스로 고른 단어 하나뿐이다.

그것으로 충분하다.
