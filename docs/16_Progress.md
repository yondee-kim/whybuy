# WHYBUY 개발 진행 상황

최종 갱신: 2026-07-24

---

# 이 문서의 목적

지금 어디까지 왔고

다음에 무엇을 할지 한눈에 본다.

작업을 마칠 때마다 갱신한다.

---

# 한 줄 요약

편이가 쇼핑앱 위에 나타나

대화를 나누고

규칙에 따라 등장 여부를 판정하는 것까지 동작한다.

기록 저장과 사용자 설정 화면이 남았다.

---

# 완료

## 프로젝트 설정

| 항목 | 값 |
|---|---|
| 패키지명 | com.whybuy.app |
| minSdk | 26 |
| targetSdk | 36 |
| compileSdk | 37 |
| 언어 | Kotlin |
| UI | Jetpack Compose (앱 화면) / View (Overlay) |
| DB | Room + KSP |
| 저장소 | GitHub Private (yondee-kim/whybuy) |

---

### 겪은 문제와 해결

| 문제 | 원인 | 해결 |
|---|---|---|
| kotlin.android 플러그인 충돌 | AGP 9가 Kotlin을 자동 적용 | 플러그인 선언 제거 |
| kotlinOptions 인식 불가 | AGP 9에서 제거됨 | kotlin { compilerOptions } 사용 |
| AAR metadata 오류 | compileSdk가 라이브러리 요구보다 낮음 | compileSdk 37 |
| Theme.AppCompat not found | appcompat 미포함 | Theme.Material 계열로 교체 |
| KSP source set 충돌 | AGP 9 built-in Kotlin | android.disallowKotlinSourceSets=false |
| Repository not found | Private 저장소 인증 실패 | URL에 계정명 명시 |

---

## 1. 앱 실행 감지

### 구현

- `AppDetector` — UsageStatsManager 기반 전경 앱 조회
- `AppWatchService` — Foreground Service, 1초 폴링
- 화면 On/Off 시 폴링 제어 (BroadcastReceiver)

### 검증

쿠팡 실행 시 Logcat에 패키지명 출력 확인

```
D/WhyBuy: 앱 전환 감지 >>> com.coupang.mobile
```

### 판단

AccessibilityService를 쓰지 않았다.

Play 정책 리스크가 크기 때문이다.

UsageStatsManager로 충분히 동작한다.

---

## 2. Overlay

### 구현

- `OverlayController` — WindowManager 기반
- TYPE_APPLICATION_OVERLAY
- FLAG_NOT_FOCUSABLE, FLAG_LAYOUT_NO_LIMITS
- 등장 1000ms / 퇴장 600ms, Bounce 없음
- 화면 우하단 배치

### 판단

Compose 대신 일반 View로 구현했다.

Overlay 자체 동작을 먼저 검증하기 위해서다.

나중에 전환 가능하다.

---

## 3. 대화 플로우

### 구현

```
GREETING → EMOTION → DECISION → FAREWELL
```

| 단계 | 내용 | 타임아웃 |
|---|---|---|
| GREETING | 잠깐 같이 생각해도 될까요? | 8초 |
| EMOTION | 감정 5종 + 건너뛰기 | 30초 |
| DECISION | 지금 산다 / 더 본다 / 다음에 | 30초 |
| FAREWELL | 결정별 응답 후 자동 소멸 | 2.5초 |

### 정의된 코드값

```
Emotion   : TIRED, BROWSING, REWARD, NEEDED, UNKNOWN
Decision  : BUY_NOW, THINK_MORE, LATER
EndReason : COMPLETED, DECLINED, DISMISSED_TIMEOUT, APP_CLOSED
```

---

## 4. Room

### 테이블

| 테이블 | 용도 | 상태 |
|---|---|---|
| target_app | 감시 대상 앱 | 사용 중 |
| app_rule | 앱별 만남 규칙 | 사용 중 |
| encounter_state | 규칙 판정용 카운터 | 사용 중 |
| encounter_log | 만남 기록 | 정의만 됨, 미사용 |

### 유틸

- `ServiceDate` — 새벽 4시 기준 날짜/주차 계산

---

## 5. RuleEngine

### 판정 순서

```
대상 앱인가
  ↓
잠시 멈춤 상태인가
  ↓
요일 조건 통과
  ↓
시간대 조건 통과 (자정 넘김 지원)
  ↓
빈도 조건 통과
  ↓
Show
```

### 지원 규칙

| 축 | 값 |
|---|---|
| 빈도 | EVERY_TIME, ONCE_A_DAY, MAX_PER_DAY, ONCE_A_WEEK, MIN_INTERVAL |
| 시간대 | ALWAYS, ONLY_BETWEEN, EXCEPT_BETWEEN |
| 요일 | EVERYDAY, WEEKDAY, WEEKEND, CUSTOM (비트마스크) |

### 검증

MIN_INTERVAL 1분으로 설정 후

연속 실행 시 `FREQUENCY_LIMIT`으로 건너뛰는 것 확인

---

# 미완

## 즉시 (출시 전 필수)

### A. 기록 저장

현재 대화 결과가 Logcat에만 남는다.

`encounter_log`에 실제로 저장해야 한다.

동시에 분석용 필드를 추가한다.

상세는 `17_Data_Strategy.md` 참고.

---

### B. 감정 분류축

감정 코드에 valence / arousal 축을 부여한다.

지금 두 칸 추가하는 비용으로

나중에 분석 가능한 데이터가 된다.

---

### C. 앱 선택 화면

지금은 쿠팡이 코드에 하드코딩되어 있다.

(`seedTestDataIfEmpty`)

사용자가 직접 고를 수 있어야 한다.

---

### D. 규칙 설정 화면

WHYBUY의 차별점이다.

RuleEngine은 이미 동작하므로

UI만 붙이면 된다.

---

### E. 홈 / 기록 화면

오늘 만난 횟수

기록 목록

삭제

---

### F. 권한 온보딩

지금은 개발용 버튼 나열 화면이다.

07 문서의 S03 설계대로 다시 만든다.

---

## 이후

- 편이 일러스트 및 애니메이션
- 서비스 재시작 (부팅, 앱 업데이트)
- 제조사 절전 대응 안내
- 접근성 (TalkBack)
- 다크모드
- 소셜 로그인
- 백업 / 복원
- 서버 및 AWS

---

# 다음 작업 순서

| 순서 | 작업 | 예상 | 근거 |
|---|---|---|---|
| 1 | 로그 저장 + 필드 확장 | 반나절 | 지금 안 하면 과거 데이터 영구 소실 |
| 2 | 감정 분류축 추가 | 10분 | 비용 대비 효과 |
| 3 | 앱 선택 화면 | 1~2일 | 하드코딩 제거 |
| 4 | 규칙 설정 화면 | 2~3일 | 차별점 |
| 5 | 홈 / 기록 화면 | 2일 | |
| 6 | 권한 온보딩 재작성 | 1일 | |
| 7 | 편이 일러스트 적용 | 미정 | 이미지 확보 후 |
| 8 | 출시 준비 | 1주 | 14 문서 참고 |

---

# 로드맵 대비

12번 문서 기준

| 계획 | 실제 |
|---|---|
| W1: 앱 감지 | 완료 |
| W2: Overlay | 완료 |
| W3: 편이 1차 + 플로우 | 플로우 완료, 이미지 미착수 |
| W4: Room + RuleEngine | 완료 |

Month 1 목표를 예정보다 빠르게 통과했다.

편이 일러스트만 남았고

이건 개발이 아니라 디자인 작업이다.

---

# 미해결 리스크

| 리스크 | 상태 | 대응 |
|---|---|---|
| specialUse FGS 심사 | 미검증 | 최소 빌드로 조기 심사 |
| 제조사 절전으로 서비스 종료 | 미대응 | 안내 가이드 |
| 편이 일러스트 미확보 | 진행 중 | 임시 도형으로 개발 지속 |
| 폴링 배터리 소모 | 미측정 | 24시간 실사용 측정 필요 |

---

# 커밋 이력

```
chore: 프로젝트 초기 설정 및 기획 문서
docs: Week 1 개발일지 추가
feat: Overlay 3단계 대화 플로우 구현
chore: Room 및 KSP 의존성 추가
feat: Room 기반 로컬 저장소 및 RuleEngine 규칙 판정 구현
```
