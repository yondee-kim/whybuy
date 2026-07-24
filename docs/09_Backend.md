# WHYBUY Backend

Version 1.0

---

# 서버의 역할

WHYBUY의 서버는

사용자를 분석하지 않는다.

서버가 하는 일은 두 가지뿐이다.

1. 로그인
2. 백업과 복원

---

## 서버가 하지 않는 것

- 감정 분석
- 소비 패턴 추론
- 광고 타겟팅용 프로파일링
- 실시간 로그 수집
- 사용자 행동 추적

---

이것은

기술적 제약이 아니라

브랜드 약속이다.

서버 설계는

약속을 지킬 수 있는 최소 구조로 만든다.

---

# 기술 스택

| 항목 | 선택 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.x |
| 빌드 | Gradle |
| DB | MySQL 8 (RDS) |
| ORM | Spring Data JPA |
| 마이그레이션 | Flyway |
| 인증 | JWT |
| 문서 | SpringDoc OpenAPI |
| 로깅 | Logback + JSON |
| 테스트 | JUnit5 + Testcontainers |

---

# 패키지 구조

```text
com.whybuy.server

├── WhyBuyApplication.java
│
├── global/
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── JpaConfig.java
│   │   ├── WebConfig.java
│   │   └── SwaggerConfig.java
│   ├── security/
│   │   ├── JwtProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── CustomUserDetails.java
│   │   └── AuthUser.java          (@AuthenticationPrincipal 해석)
│   ├── error/
│   │   ├── ErrorCode.java
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   ├── response/
│   │   ├── ApiResponse.java
│   │   └── PageResponse.java
│   └── util/
│
├── auth/
│   ├── controller/AuthController.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── GoogleTokenVerifier.java
│   │   └── TokenService.java
│   ├── dto/
│   └── domain/RefreshToken.java
│
├── user/
│   ├── controller/UserController.java
│   ├── service/UserService.java
│   ├── domain/
│   │   ├── User.java
│   │   ├── SocialProvider.java
│   │   └── UserStatus.java
│   ├── repository/UserRepository.java
│   └── dto/
│
├── backup/
│   ├── controller/BackupController.java
│   ├── service/BackupService.java
│   ├── domain/BackupSnapshot.java
│   ├── repository/
│   └── dto/
│
├── content/
│   ├── controller/ContentController.java
│   ├── service/ContentService.java
│   └── domain/
│       ├── Notice.java
│       └── AppMeta.java
│
└── admin/
    └── (Phase 2)
```

---

## 패키지 원칙

기술이 아니라

도메인으로 나눈다.

controller / service / repository로

최상위를 나누지 않는다.

---

# 인증

## 흐름

```text
[App]
Google 로그인
↓
idToken 획득
↓
POST /api/v1/auth/google  { idToken }
↓
[Server]
Google 공개키로 idToken 검증
↓
sub(고유 ID)로 User 조회 또는 생성
↓
accessToken + refreshToken 발급
↓
[App]
토큰 저장
```

---

## idToken 검증

서버는

Google의 공개키로

idToken 서명을 직접 검증한다.

---

확인 항목

- 서명
- iss (accounts.google.com)
- aud (WHYBUY 클라이언트 ID)
- exp

---

```java
@Component
@RequiredArgsConstructor
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleUserInfo verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
            }
            GoogleIdToken.Payload payload = token.getPayload();
            return new GoogleUserInfo(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name")
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new BusinessException(ErrorCode.SOCIAL_VERIFY_FAILED);
        }
    }
}
```

---

## 토큰 정책

| 토큰 | 만료 | 저장 |
|---|---|---|
| accessToken | 30분 | 앱 메모리 + DataStore |
| refreshToken | 60일 | DB + 앱 암호화 저장 |

---

refreshToken은

DB에 해시로 저장한다.

원문을 저장하지 않는다.

---

## 재발급

refresh 요청 시

새 refreshToken을 함께 내려준다.

기존 토큰은 즉시 폐기한다.

(Rotation)

---

재사용이 감지되면

해당 사용자의 모든 refreshToken을 무효화한다.

---

## SecurityConfig

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/content/**",
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter,
                UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

---

# 공통 응답

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorBody error
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code) {
        return new ApiResponse<>(false, null,
            new ErrorBody(code.name(), code.getMessage()));
    }
}
```

---

## 오류 메시지 원칙

서버 메시지도

WHYBUY 말투를 따른다.

---

앱에 그대로 노출될 수 있으므로

기술 용어를 쓰지 않는다.

---

| 코드 | 메시지 |
|---|---|
| INVALID_SOCIAL_TOKEN | 로그인 정보를 확인하지 못했어요. |
| TOKEN_EXPIRED | 다시 로그인해 주시겠어요? |
| BACKUP_NOT_FOUND | 아직 저장된 기록이 없어요. |
| INTERNAL_ERROR | 지금은 연결이 어려운가 봐요. |

---

# API 목록

## Auth

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | /api/v1/auth/google | X | 구글 로그인 |
| POST | /api/v1/auth/refresh | X | 토큰 재발급 |
| POST | /api/v1/auth/logout | O | 로그아웃 |

---

### POST /api/v1/auth/google

Request

```json
{ "idToken": "..." }
```

Response

```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "isNewUser": true
  }
}
```

---

## User

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | /api/v1/users/me | O | 내 정보 |
| DELETE | /api/v1/users/me | O | 탈퇴 |

---

### GET /api/v1/users/me

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "nickname": "편이와 함께",
    "provider": "GOOGLE",
    "createdAt": "2026-07-24T10:00:00",
    "lastBackupAt": "2026-07-24T22:10:00"
  }
}
```

---

이메일은 응답에 포함하지 않는다.

앱이 필요로 하지 않는다.

---

### DELETE /api/v1/users/me

즉시 처리

---

1. BackupSnapshot 물리 삭제
2. RefreshToken 삭제
3. User 익명화 후 30일 뒤 삭제

---

응답

```json
{ "success": true, "data": { "deleted": true } }
```

---

## Backup

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | /api/v1/backup | O | 백업 업로드 |
| GET | /api/v1/backup/latest | O | 최신 백업 조회 |
| GET | /api/v1/backup/meta | O | 백업 상태만 조회 |
| DELETE | /api/v1/backup | O | 백업 삭제 |

---

### 백업 데이터 형태

서버는

내용을 해석하지 않는다.

앱이 만든 JSON을

그대로 보관한다.

---

Request

```json
{
  "schemaVersion": 1,
  "deviceId": "hashed-device-id",
  "payload": {
    "targetApps": [ ... ],
    "rules": [ ... ],
    "encounters": [ ... ]
  }
}
```

---

### 저장 위치

| 크기 | 저장소 |
|---|---|
| 256KB 미만 | RDS (JSON 컬럼) |
| 256KB 이상 | S3, DB에는 키만 |

---

### 정책

- 사용자당 최신 1개만 보관
- 이전 스냅샷은 덮어쓰기
- 최대 5MB
- 수동 백업만 (자동 백업 없음)

---

### GET /api/v1/backup/meta

```json
{
  "success": true,
  "data": {
    "exists": true,
    "schemaVersion": 1,
    "sizeBytes": 48213,
    "updatedAt": "2026-07-24T22:10:00"
  }
}
```

---

앱은 복원 전에

meta만 먼저 조회한다.

큰 데이터를 불필요하게 받지 않기 위해서다.

---

## Content

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | /api/v1/content/notices | X | 공지 목록 |
| GET | /api/v1/content/app-meta | X | 추천 앱 목록 |
| GET | /api/v1/content/config | X | 앱 설정값 |

---

### app-meta

추천 앱 목록을

서버에서 내려준다.

앱 업데이트 없이

새 쇼핑앱을 추가할 수 있다.

---

```json
{
  "success": true,
  "data": {
    "version": 12,
    "apps": [
      {
        "packageName": "com.coupang.mobile",
        "displayName": "쿠팡",
        "category": "SHOPPING",
        "recommendedFrequency": "ONCE_A_DAY"
      }
    ]
  }
}
```

---

### config

```json
{
  "minSupportedVersion": 10,
  "latestVersion": 14,
  "forceUpdate": false,
  "noticeBanner": null
}
```

---

# 도메인 규칙

## User

- provider + providerId 조합은 유일
- 이메일은 저장하되 응답에 쓰지 않는다
- 닉네임은 서버가 자동 생성

---

## 닉네임 생성

랜덤 조합

예)

조용한 편이

따뜻한 편이

느긋한 편이

---

사용자가 입력하지 않는다.

가입 단계를 늘리지 않기 위해서다.

---

# 보안

- 모든 통신 HTTPS
- JWT 서명키는 환경변수 (Parameter Store)
- 요청 로그에 payload 미기록
- Rate Limit: 인증 API 분당 10회
- CORS: 앱만 사용하므로 최소 허용

---

## 로그 정책

기록한다

- 요청 경로
- 상태 코드
- 응답 시간
- userId

---

기록하지 않는다

- 백업 payload
- idToken
- 감정 데이터
- 앱 목록

---

# 성능

MVP 예상 트래픽

- 로그인: 일 수백 건
- 백업: 사용자당 주 1회 미만

---

t3.small 단일 인스턴스로 충분하다.

과설계하지 않는다.

---

# 확장 계획

## Phase 2

- 공지 관리 어드민
- 앱 메타 관리 어드민
- 통계 (익명 집계)

---

## Phase 3

- AI 월간 요약
  - 단, 원문 감정은 서버로 보내지 않는다
  - 집계된 코드만 전송
- 푸시 알림

---

## AI 도입 시 원칙

서버는

사용자의 문장을 받지 않는다.

감정 코드와 횟수만 받는다.

---

예)

전송한다

TIRED: 8, REWARD: 3

---

전송하지 않는다

"오늘 회의 때문에 힘들었어요"

---

# 로컬 실행

```bash
docker compose up -d   # mysql
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## Profile

| profile | 용도 |
|---|---|
| local | 개발 |
| dev | 개발 서버 |
| prod | 운영 |
