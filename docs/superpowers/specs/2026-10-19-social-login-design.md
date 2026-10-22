# 모바일 앱 소셜 로그인 + 인증 구조 개편 설계

- 작성일: 2026-10-19
- 브랜치: `feat/social-login`
- 상태: 검토 대기

## 1. 배경과 목표

### 배경
이 서비스는 원래 로그인 개념이 없었다. 앱이 FCM 토큰을 `deviceId`라는 이름으로 보내면 서버가 회원을 찾아서 쓰거나 새로 만들어 JWT를 발급했다. FCM 토큰 하나가 계정 식별자, 로그인 수단, 푸시 주소 역할을 모두 맡고 있었다.

기존 구조에서 확인된 문제는 다음과 같다.

1. **access 토큰만으로 refresh 토큰을 새로 받을 수 있다.** `JwtAuthFilter.ensureValidRefresh`가 refresh 헤더가 없거나 무효면 새 refresh를 발급해 응답 헤더에 실어준다. access가 유출되면 영구 접근이 가능하다.
2. **FCM 토큰만 알면 로그인된다.** `POST /auth/login`은 `deviceId`(FCM 토큰)만 받는다. 또 FCM 토큰은 재설치나 갱신 시 바뀌므로 유저가 계정을 잃는다.
3. **refresh 토큰이 서버에 저장되지 않는다.** 그래서 로그아웃, 무효화, 탈취 감지가 불가능하다. 새 refresh를 줘도 이전 refresh가 만료 전까지 유효하므로 회전이 의미 없다.
4. **관리자 권한을 필터 안의 경로 하드코딩으로 막는다.** Spring Security 권한이 비어 있어서, 새 관리자 API를 목록에 추가하지 않으면 일반 유저도 호출할 수 있다.
5. **모든 요청에 refresh 토큰을 보내고, 필터가 아무 응답에나 새 토큰을 끼워 준다.** 노출 면이 넓고, 동시 요청 시 앱의 토큰 저장이 꼬이기 쉽다.
6. 그 외
   - 전역 예외 처리가 없어 `IllegalArgumentException`이 500이 된다.
   - 관리자 로그인 메시지가 아이디 존재 여부를 노출한다.
   - 필터가 `@Component`라서 두 번 등록된다.
   - 요청마다 `lastActiveAt` UPDATE와 INFO 로그가 발생한다.
   - JWT에 FCM 토큰(`did`)이 들어간다.
   - 관리자 로그아웃은 서버에서 아무것도 하지 않는다.

### 목표
- 모바일 앱에 Google, Apple 소셜 가입/로그인을 추가한다. 앱을 다시 열 때 **자동 로그인**이 되어야 한다.
- 위 JWT 구조의 문제를 함께 바로잡는다.
- 관리자 페이지는 소셜 없이 기존 아이디/비밀번호 방식을 유지하되, 권한 분리와 토큰 무효화를 제대로 한다.
- 이 문서를 기준으로 앱과 관리자 웹이 각자 작업한다.

### 범위 밖
- 온보딩(`preference`, 약관 동의 등)은 앱 기획이 정해지면 별도로 추가한다.
- 계정 연동(한 회원에 구글과 애플을 함께 연결)은 이번에 만들지 않는다. 구조만 열어둔다.
- 앱 강제 업데이트용 최소 버전 API
- 관리자 로그인 시도 횟수 제한
- 기존 일기, 음악, 질문 기능 자체의 테스트 추가

## 2. 핵심 결정 요약

| 항목 | 결정 |
|---|---|
| 기존 익명(기기 기반) 유저 | 이어가지 않는다. 소셜 로그인 필수. 구버전 앱은 강제 업데이트. 기존 데이터는 배포 시 정리 SQL로 soft delete |
| 가입 흐름 | 소셜 로그인 한 번으로 가입 완료. 응답에 `isNewMember` 포함 |
| 지원 공급자 | Google(iOS, Android), Apple(iOS) |
| 공급자 간 계정 | 구글 계정과 애플 계정은 별개 회원. 이메일로 자동 병합하지 않음 |
| 기기 식별 | 앱이 처음 실행할 때 만든 UUID(`deviceId`). 이름표일 뿐 인증 수단이 아님 |
| FCM 토큰 | 계정 식별에 쓰지 않음. 기기에 붙은 푸시 주소로만 저장 |
| 앱 access 토큰 | JWT, 30분 |
| 앱 refresh 토큰 | 랜덤 문자열, 60일. DB에 SHA-256 해시로 저장. 사용할 때마다 회전, 재사용 감지 |
| 토큰 발급 위치 | 소셜 로그인과 `/auth/refresh`에서만. 응답 JSON 바디로 전달 |
| 관리자 토큰 | access만, 2시간. `admin.token_version`으로 서버에서 무효화 |
| 관리자 API 경로 | 전부 `/admin/**` 아래로 이동 |
| 서명 키 | 앱용과 관리자용 분리 |
| 토큰 검증 | Spring Security OAuth2 Resource Server. 기존 커스텀 `JwtAuthFilter` 대체 |
| 에러 응답 | RFC 9457 Problem Details + 확장 필드 `code` |
| 180일 자동 탈퇴 | 제거 |
| 기존 기기 기반 API | 코드까지 삭제 |

### 식별자 원칙
- 외부(API 응답, URL, JWT)에 노출되는 식별자는 UUID를 쓴다. `member_id`, `device_id`가 해당한다.
- auto increment는 서버 내부에서만 쓰는 식별자에 한해 허용한다. 이번 설계에서는 `social_account.id`가 해당한다.
- 참고: 기존 `diary_id`는 auto increment이고 URL에 노출된다. 조회, 수정, 삭제는 모두 `diaryId + userId`로 소유자를 확인하므로 남의 데이터에 접근할 수는 없다. 다만 규모 추정은 가능하다. 이번 범위에서는 변경하지 않는다.

## 3. 데이터 모델

### 3.1 `member` (기존 테이블 재사용)
- `member_id`(UUID 문자열, PK)는 그대로 유지한다. 일기 등 기존 데이터 연결은 바뀌지 않는다.
- 엔티티에서 `deviceId`, `mobileOS`, `preference`, `lastActiveAt` 필드를 제거한다.
- DB 컬럼(`device_id`, `mobile_os`, `preference`, `last_active_at`)은 남겨둔다. `ddl-auto=validate`는 엔티티 필드가 DB에 있는지만 검사하므로 문제없다. 컬럼 삭제는 추후 별도 SQL로 한다.
- `member_id`는 서버가 처음 보는 `(provider, sub)`로 로그인할 때 `UUID.randomUUID()`로 생성한다.

### 3.2 `social_account` (신규)
소셜 계정과 회원을 연결한다. 한 회원에 여러 소셜 계정이 붙을 수 있는 구조다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT, PK, AUTO_INCREMENT | 내부 전용, 외부 노출 금지 |
| `member_id` | VARCHAR(255), NOT NULL | 회원 |
| `provider` | VARCHAR(20), NOT NULL | `GOOGLE`, `APPLE` |
| `provider_user_id` | VARCHAR(255), NOT NULL | 공급자의 고유 ID(ID 토큰의 `sub`) |
| `email` | VARCHAR(320), NULL | 참고용. 애플은 첫 로그인 때만 주며 가린 주소일 수 있음 |
| `apple_refresh_token` | VARCHAR(1024), NULL | 애플 revoke용. AES-GCM으로 암호화해 저장. 애플이 아니면 NULL |
| `created_at` | DATETIME(6), NOT NULL | |

- `UNIQUE (provider, provider_user_id)`, `INDEX (member_id)`
- 인조 PK를 쓰는 이유: 앱을 다른 Apple 개발자 팀으로 이관하면 애플 `sub`가 바뀐다. 이때 PK가 아닌 일반 컬럼만 갱신하면 된다.
- 계정 식별 기준은 **검증된 ID 토큰에서 꺼낸 `(provider, sub)`**다. 앱이 보낸 다른 값은 식별에 쓰지 않는다.

### 3.3 `member_device` (신규)
기기 한 대가 로그인 세션 하나다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `device_id` | VARCHAR(36), PK | 앱이 만든 UUID |
| `member_id` | VARCHAR(255), NOT NULL | 지금 이 기기에 로그인한 회원 |
| `os` | VARCHAR(10), NOT NULL | `AND`, `IOS` |
| `fcm_token` | VARCHAR(512), NULL, UNIQUE | 푸시 주소 |
| `refresh_token_hash` | VARCHAR(64), NOT NULL, UNIQUE | 현재 refresh 토큰의 SHA-256(hex) |
| `prev_refresh_token_hash` | VARCHAR(64), NULL | 직전 refresh 해시. 재사용 감지용 |
| `rotated_at` | DATETIME(6), NULL | 마지막 회전 시각 |
| `refresh_expires_at` | DATETIME(6), NOT NULL | refresh 만료 시각 |
| `last_used_at` | DATETIME(6), NOT NULL | 마지막 로그인 또는 refresh 시각 |
| `created_at` | DATETIME(6), NOT NULL | |

- `INDEX (member_id)`, `INDEX (prev_refresh_token_hash)`
- `member.last_active_at`의 역할은 `last_used_at`이 대신한다. 요청마다가 아니라 로그인이나 refresh 때만 갱신한다.

### 3.4 `admin` (기존 테이블에 컬럼 추가)
- `token_version INT NOT NULL DEFAULT 0` 컬럼을 추가한다.
- 관리자 토큰에 `ver` claim으로 넣는다. 로그아웃이나 삭제 때 +1 해서 기존 토큰을 모두 무효화한다.

### 3.5 같은 기기에서 사용자가 바뀌는 경우
- 원칙: 기기 한 대(`device_id`)에는 로그인한 회원이 한 명뿐이다.
- **로그아웃 후 다른 사람이 로그인하는 경우**: 로그아웃 때 기기 행을 삭제하고, 다음 로그인 때 새 행을 만든다.
- **로그아웃 없이 다른 사람이 로그인하는 경우**(iOS 재설치 후 키체인 UUID 유지 등): 같은 행을 덮어쓴다.
  - `member_id`, `refresh_token_hash`, `refresh_expires_at`, `os`, `fcm_token`을 새 값으로 바꾼다.
  - `prev_refresh_token_hash`와 `rotated_at`은 NULL로 비운다. 이렇게 해야 이전 사람의 직전 토큰이 유예 구간에 통과하지 못한다.
- **FCM 토큰 유니크**: `fcm_token`을 저장할 때 같은 값을 가진 다른 행이 있으면 그 행의 `fcm_token`을 NULL로 만든다. 푸시 주소 하나는 항상 한 회원에게만 연결된다.
- `(device_id, member_id)` 복수 행 방식은 쓰지 않는다. 한 폰의 FCM 토큰이 여러 회원에 걸리면 다른 사람의 일기 알림이 그 폰에 뜬다.
- 감수하는 위험: 남의 `device_id`를 아는 사람이 자기 소셜 계정으로 로그인하면, 그 기기의 기존 세션을 끊을 수 있다. 데이터 접근은 불가능하다. `device_id`는 어떤 응답이나 토큰에도 노출하지 않는다.

### 3.6 DDL
- 마이그레이션 도구가 없으므로 적용 SQL을 `docs/db/`에 파일로 둔다.
  - `docs/db/2026-10-19-social-login-schema.sql`: 3.2~3.4의 생성 및 변경
  - `docs/db/2026-10-19-cleanup-anonymous-members.sql`: 기존 익명 유저 정리 (8장 참고)

## 4. 토큰

### 4.1 앱 유저 access 토큰 (JWT)
- 서명: HS256, 키 `jwt.member.secret`
- claims:
  - `iss` = `jwt.issuer`
  - `aud` = `app`
  - `sub` = `member_id`
  - `role` = `USER`
  - `iat`, `exp`
- 유효기간: 30분(`jwt.member.access-token-ttl`, 기본값 `30m`)
- `deviceId`와 FCM 토큰은 넣지 않는다.

### 4.2 앱 유저 refresh 토큰 (opaque)
- 32바이트 `SecureRandom` 값을 base64url로 인코딩한다.
- DB에는 SHA-256 해시만 저장한다. 엔트로피가 충분한 랜덤 값이라 bcrypt 같은 느린 해시는 필요 없다.
- 유효기간: 60일(`jwt.member.refresh-token-ttl`, 기본값 `60d`). 회전할 때마다 새로 60일이 시작된다. 60일 안에 한 번이라도 앱을 열면 로그인이 계속 유지된다.

### 4.3 refresh 회전과 재사용 감지 (`POST /auth/refresh`)
요청으로 `refreshToken`과 `deviceId`를 받아 `h = sha256(refreshToken)`을 계산한다.

1. `device_id = deviceId AND refresh_token_hash = h` 행이 있으면:
   - 만료 전이면 새 토큰 한 쌍을 발급한다. 기존 해시는 `prev_refresh_token_hash`로 옮기고 `rotated_at = now`로 둔다. 새 해시를 저장하고 `refresh_expires_at`과 `last_used_at`을 갱신한다.
   - 만료됐으면 행을 삭제하고 `REFRESH_INVALID`를 응답한다.
2. 위 행이 없고 `device_id = deviceId AND prev_refresh_token_hash = h` 행이 있으면:
   - `now - rotated_at ≤ 60초`이면 네트워크 재시도로 판단한다. 1번과 같이 새 토큰 한 쌍을 다시 발급하고 회전한다.
   - 60초를 넘었으면 탈취로 판단한다. 행을 삭제하고 `REFRESH_REUSED`를 응답한다.
3. 둘 다 없으면 `REFRESH_INVALID`를 응답한다.

- refresh 처리는 해당 기기 행을 비관적 락(`SELECT ... FOR UPDATE`)으로 잡고 한 트랜잭션에서 수행한다.
- 앱은 refresh 요청을 동시에 하나만 보낸다. 여러 요청이 동시에 401을 받으면 refresh는 한 번만 하고 나머지는 그 결과를 기다린다.

### 4.4 관리자 access 토큰 (JWT)
- 서명: HS256, 키 `jwt.admin.secret`(앱 키와 다름)
- claims:
  - `iss` = `jwt.issuer`
  - `aud` = `admin`
  - `sub` = `admin_id`
  - `role` = `ADMIN` 또는 `SUPER_ADMIN`
  - `ver` = `token_version`
  - `iat`, `exp`
- 유효기간: 2시간(`jwt.admin.access-token-ttl`, 기본값 `2h`). refresh는 없고, 만료되면 다시 로그인한다.
- 매 요청마다 관리자 행을 조회한다(기존과 동일). 승인 상태가 아니거나, 삭제됐거나, `ver`가 다르면 `TOKEN_INVALID`다.

## 5. 소셜 토큰 검증

### 5.1 공통
- `spring-security-oauth2-jose`의 `NimbusJwtDecoder.withJwkSetUri(...)`로 공급자 공개키(JWKS)를 받아 서명을 검증한다. 키는 캐시하고, 모르는 `kid`가 오면 다시 받는다.
- 검증 항목: 서명, `iss`, `aud`, `exp`(시계 오차 허용 60초)
- JWKS를 받아오지 못하면 `SOCIAL_PROVIDER_UNAVAILABLE`(503), 검증에 실패하면 `SOCIAL_TOKEN_INVALID`(401)를 응답한다.

### 5.2 Google
- JWKS: `https://www.googleapis.com/oauth2/v3/certs`
- `iss`: `https://accounts.google.com` 또는 `accounts.google.com`
- `aud`: `oauth.google.client-ids`에 설정한 값 중 하나여야 한다.
  - iOS는 iOS OAuth 클라이언트 ID다.
  - Android는 Google Sign-In에 넘긴 server(web) client ID다.
- `email`은 있으면 참고용으로 저장한다.

### 5.3 Apple
- JWKS: `https://appleid.apple.com/auth/keys`
- `iss`: `https://appleid.apple.com`
- `aud`: `oauth.apple.client-id`(iOS 앱 번들 ID)
- **nonce**
  - 앱이 랜덤 원본 nonce를 만들고, 그 SHA-256(hex) 값을 애플 로그인 요청에 넣는다.
  - 서버는 요청으로 받은 원본 nonce를 SHA-256(hex)한 값이 토큰의 `nonce` claim과 같은지 확인한다. 다르면 `SOCIAL_TOKEN_INVALID`다.
- **authorization code 교환**: 애플 탈퇴 revoke에 필요한 애플 refresh 토큰을 받기 위해 한다.
  - `POST https://appleid.apple.com/auth/token`을 `grant_type=authorization_code`로 호출한다.
  - client_secret은 ES256 JWT다. 서명 키는 `.p8`이고, claims는 `iss` = Team ID, `sub` = client ID, `aud` = `https://appleid.apple.com`, 헤더 `kid` = Key ID다. 유효기간은 최대 6개월이므로 짧게 만들어서 쓴다.
  - 받은 refresh 토큰은 AES-GCM으로 암호화해 `social_account.apple_refresh_token`에 저장한다.
  - **교환이 실패해도 로그인은 성공시킨다.** WARN 로그를 남기고, 저장된 값이 없으면 다음 로그인 때 다시 시도한다(애플은 로그인마다 새 code를 준다).
- **revoke(탈퇴 시)**: `POST https://appleid.apple.com/auth/revoke`를 `token_type_hint=refresh_token`으로 호출한다. 실패해도 탈퇴는 진행하고 ERROR 로그를 남긴다.
- Android에서의 애플 로그인은 이번 범위가 아니다(웹 기반 흐름과 Services ID가 필요하다). `client-id` 설정을 목록으로 확장하면 나중에 추가할 수 있다.

## 6. API

- 인증이 필요한 요청은 `Authorization: Bearer <access>` 헤더를 붙인다.
- 토큰은 응답 JSON 바디로 전달한다. 응답 헤더의 `Authorization`과 `Refresh-Token`은 더 이상 쓰지 않는다.

### 6.1 앱 인증

**`POST /auth/social/google`** (공개)
```json
요청 {
  "idToken": "string",
  "deviceId": "UUID 문자열",
  "os": "AND | IOS",
  "fcmToken": "string | null"
}
응답 200 {
  "accessToken": "string",
  "accessTokenExpiresIn": 1800,
  "refreshToken": "string",
  "refreshTokenExpiresIn": 5184000,
  "isNewMember": true,
  "nickname": null
}
```

**`POST /auth/social/apple`** (공개)
```json
요청 {
  "identityToken": "string",
  "authorizationCode": "string",
  "nonce": "원본 nonce",
  "deviceId": "UUID 문자열",
  "os": "IOS",
  "fcmToken": "string | null"
}
응답 200: Google과 동일
```

소셜 로그인 처리 순서:
1. 소셜 토큰을 검증하고 `sub`를 얻는다.
2. `social_account`에서 `(provider, sub)`를 찾는다. 없으면 `member`와 `social_account`를 만들고 `isNewMember = true`로 둔다. 신규 회원이면 기존처럼 Redis refsets 시드를 넣는다.
   - 동시에 첫 로그인 요청이 두 번 오면 유니크 제약 위반이 날 수 있다. 이때는 다시 조회해 기존 회원으로 처리한다.
3. 애플이면 authorization code 교환을 시도한다(5.3).
4. `member_device`를 upsert한다(3.5 규칙 적용).
5. access와 refresh를 발급한다.

**`POST /auth/refresh`** (공개)
```json
요청 { "refreshToken": "string", "deviceId": "UUID 문자열" }
응답 200: 로그인 응답과 동일하되 isNewMember 없음
```
처리 규칙은 4.3과 같다.

**`POST /auth/logout`** (USER)
```json
요청 { "deviceId": "UUID 문자열" }
응답 204
```
내 회원 소유인 해당 기기 행을 삭제한다. 행이 없어도 204를 응답한다(멱등).

**`PUT /auth/devices/fcm-token`** (USER)
```json
요청 { "deviceId": "UUID 문자열", "fcmToken": "string" }
응답 204
```
내 소유 기기만 바꿀 수 있고, 아니면 `DEVICE_NOT_FOUND`(404)다. FCM 토큰 유니크 규칙(3.5)을 적용한다.

### 6.2 앱 회원
- **`GET /users/me`** (USER): 내 정보. `{ "memberId": "UUID 문자열", "nickname": "달콤한조각" | null }`. 기존 `GET /me`(문자열 응답)는 제거했다(2026-10-22).
- **`PUT /users/me/nickname`** (USER): 닉네임 설정·수정. 가입 흐름(로그인 → 약관 → 닉네임)과 마이페이지가 같은 API 를 쓴다.
  ```json
  요청 { "nickname": "달콤한조각" }
  응답 200 { "nickname": "달콤한조각" }
  ```
  규칙은 완성형 한글·영문·숫자 2~10자(`^[가-힣A-Za-z0-9]{2,10}$`)이고 그 외는 `VALIDATION_FAILED`(400)다. 공백은 자르지 않고 거부한다.
  유니크 제약은 없다(다른 회원의 닉네임을 볼 일이 없다). 소셜 토큰의 이름은 저장하지 않으며, 미리 채우기는 앱이 SDK 값으로 한다.
- 소셜 로그인·refresh 응답(6.1)에는 `nickname` 이 함께 내려간다. 가입 직후와 닉네임을 아직 정하지 않은 기존 회원은 `null` 이고, 앱은 그때 닉네임 설정 화면으로 보낸다.
- **`DELETE /users`** (USER, 탈퇴): 경로는 유지한다. 처리 순서는 다음과 같다.
  1. 애플 계정이면 revoke를 호출한다(실패해도 계속 진행).
  2. 해당 회원의 `social_account`, `member_device` 행을 삭제한다.
  3. 기존 로직대로 일기, 색상, 이미지를 soft delete하고, 회원을 soft delete하고, Redis 유저 데이터를 삭제한다.
- 탈퇴 후 같은 소셜 계정으로 다시 로그인하면 새 `member_id`로 새로 가입된다. 예전 데이터는 복구되지 않는다.

### 6.3 관리자 (전부 `/admin` 아래)
| 기존 | 변경 |
|---|---|
| `POST /admins` (가입, 공개) | `POST /admin/auth/signup` (공개) |
| `POST /admins/login` (공개) | `POST /admin/auth/login` (공개) |
| `POST /admins/logout` | `POST /admin/auth/logout` |
| `POST /admins/approve` | `POST /admin/admins/approve` |
| `GET /admins` | `GET /admin/admins` |
| `DELETE /admins` | `DELETE /admin/admins` |
| `/questions/**` | `/admin/questions/**` |
| `/dashboards/**` | `/admin/dashboards/**` |
| `/musics/**` | `/admin/musics/**` |
| `/fcmMessages` | `/admin/fcm-messages` |
| `/debug/fcm/**` | `/admin/debug/fcm/**` |

- 관리자 로그인 응답 형식이 바뀐다. 기존에는 access를 헤더로, `isSuper`를 바디로 줬다. 앞으로는 모두 바디로 준다.
  ```json
  { "accessToken": "string", "accessTokenExpiresIn": 7200, "isSuper": false }
  ```
- 로그아웃은 `token_version`을 +1 하고 204를 응답한다.
- 관리자 삭제 때도 대상 관리자의 `token_version`을 +1 한다.

### 6.4 제거되는 API와 동작
- `POST /users` (FCM 토큰 기반 가입)
- `POST /auth/login` (FCM 토큰 기반 로그인)
- 헤더 방식 `POST /auth/refresh`
- 필터의 몰래 갱신, `Refresh-Token` 헤더, 응답 헤더로 토큰 전달

## 7. 보안 구성

### 7.1 Security 체인 구성
| 순서 | 체인 | 대상 | 인증 |
|---|---|---|---|
| 1 | Swagger | Swagger 경로 | 기존 그대로(HTTP Basic) |
| 2 | 관리자 | `/admin/**` | 관리자 키 디코더. `/admin/auth/signup`, `/admin/auth/login`은 permitAll, 나머지는 `hasAnyRole('ADMIN','SUPER_ADMIN')` |
| 3 | 앱 | 나머지 전부 | 앱 키 디코더. `/auth/social/**`, `/auth/refresh`, `/health`, `/error`는 permitAll, 나머지는 `hasRole('USER')` |

- `/admin` 아래에 만든 API는 자동으로 관리자 전용이 된다. 앱 체인의 기본값은 `ROLE_USER`만 허용이므로, 관리자 토큰으로 앱 API를 호출할 수 없다(키가 달라 검증 단계에서 실패한다).
- 슈퍼관리자 전용 기능(승인, 목록, 삭제)은 기존처럼 서비스에서 확인한다.
- 모든 체인은 STATELESS, CSRF 비활성을 유지한다. CORS 설정은 기존 그대로 둔다.

### 7.2 토큰 검증
- `oauth2ResourceServer().jwt()`로 검증한다. 체인마다 자기 키로 만든 `NimbusJwtDecoder`를 쓰고, validator로 `iss`, `aud`를 확인한다.
- `JwtAuthenticationConverter`가 `role` claim을 `ROLE_*` 권한으로 바꾼다. `role`이 없으면 거부한다.
- **계정 생존 확인**
  - 앱: `member` PK로 조회해 `is_valid = 1`인지 확인한다.
  - 관리자: `admin_id`로 조회해 `is_valid = 1`이고 `ver`가 일치하는지 확인한다.
  - 실패하면 `TOKEN_INVALID`다.
  - 앱 회원 확인을 남기는 이유: 탈퇴 직후 남은 access로 30분 동안 일기를 쓰는 일을 막기 위해서다.
- **공개 경로에서는 Bearer 토큰을 읽지 않는다.** permitAll 경로에서 `BearerTokenResolver`가 null을 돌려주도록 한다. 만료된 access를 붙인 채 `/auth/refresh`를 불러도 정상 동작해야 하기 때문이다.
- `Authentication.getName()`은 지금처럼 `member_id`(또는 `admin_id`)를 돌려준다. 그래서 기존 컨트롤러와 서비스의 `auth.getName()` 사용은 바뀌지 않는다.
- 기존 `JwtAuthFilter`는 삭제한다. 이와 함께 필터 중복 등록, Swagger 경로 우회 코드, 요청마다의 INFO 로그와 `lastActiveAt` 갱신도 사라진다.

### 7.3 비밀 값 저장
- 애플 refresh 토큰은 AES-256-GCM으로 암호화한다. 키는 `oauth.apple.token-encryption-key`(base64, 32바이트)이고, 저장 형식은 `base64(iv || ciphertext)`다.
- 토큰 원문(소셜, access, refresh)은 로그에 남기지 않는다.

## 8. 기존 데이터와 기능 정리

### 8.1 기존 익명 유저 정리 (배포 시 1회)
`docs/db/2026-10-19-cleanup-anonymous-members.sql`:
- `is_valid = 1`인 기존 `member` 전부와 그 회원의 `diary`, `diary_color`, `diary_image`를 soft delete한다(`is_valid = 0`, `deleted_at = NOW()`). 기존 탈퇴와 같은 방식이다.
- 이후 기존 `DiaryDeleteScheduler`가 3일 뒤 DB에서 완전히 지우고 S3 이미지도 정리한다. 추가 코드는 없다.
- 유저별 Redis 데이터(refsets)는 SQL로 지울 수 없다. 키 패턴 삭제 명령을 SQL 파일 주석에 함께 적는다.
- 반드시 새 버전 서버 배포와 같은 시점에 실행한다.

### 8.2 코드 삭제
| 위치 | 삭제 내용 |
|---|---|
| `AuthController` | `POST /users`, `POST /auth/login`, 헤더 방식 `/auth/refresh` |
| `AuthService` | `issueTokens`, `loginByDeviceId`, 기존 `refresh` |
| DTO | `MemberSignupRequestDto`, `MemberLoginRequestDto`, `TokenResponseDto`(새 응답 DTO로 대체), `AdminLoginResultDto` 등 헤더 방식 관련 |
| `MemberRepository` | `findByDeviceId`, `findByDeviceIdAndIsValidTrue`, `findByIsValidTrueAndLastActiveAtBefore`, `changeLastActiveAt` |
| `security/JwtAuthFilter` | 파일 전체(Resource Server로 대체) |
| `JwtUtil` | 기존 member/admin 토큰 생성(새 토큰 발급 컴포넌트로 대체), `did` claim |
| `DiaryService` | `updateLastActiveAt` |
| `scheduler/InactiveMemberScheduler` | 파일 전체 |
| `Member` 엔티티 | `deviceId`, `mobileOS`, `preference`, `lastActiveAt` 필드 |
| `OpenApiConfig` | `Refresh-Token` 보안 스킴 |

### 8.3 푸시 발송 변경
- `FcmService`
  - 회원 단위 발송은 그 회원의 `member_device` 중 `fcm_token`이 있는 모든 기기로 보낸다. OS별 설정은 기기의 `os`를 기준으로 한다.
  - 전체 발송은 `fcm_token`이 있는 모든 기기로 보낸다.
  - FCM이 `UNREGISTERED`나 `INVALID_ARGUMENT`(토큰 무효)로 응답하면 그 기기의 `fcm_token`을 NULL로 정리한다.
- `DiaryRemindScheduler`는 `member.deviceId` 확인 대신, 푸시 가능한 기기가 있는 유효 회원을 대상으로 한다.

## 9. 에러 처리

### 9.1 응답 형식 (RFC 9457 Problem Details)
```json
HTTP 401
Content-Type: application/problem+json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "access token이 만료되었습니다.",
  "instance": "/diaries",
  "code": "TOKEN_EXPIRED"
}
```
- 클라이언트는 `code`로만 분기한다. `detail`은 사람이 읽는 설명이다.
- `@RestControllerAdvice` 하나에서 변환한다. Security 단계의 401과 403도 `AuthenticationEntryPoint`와 `AccessDeniedHandler`에서 같은 형식으로 응답한다.
- 401에는 RFC 6750 `WWW-Authenticate: Bearer ...` 헤더를 붙인다.
- 500은 `INTERNAL_ERROR`만 응답하고 내부 정보는 숨긴다. 스택 트레이스는 서버 로그에만 남긴다.

### 9.2 에러 코드
| HTTP | code | 발생 조건 | 클라이언트 동작 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | 보호된 API에 토큰 없음 | 로그인 화면 |
| 401 | `TOKEN_EXPIRED` | access 만료 | `/auth/refresh` 1회 후 원요청 재시도 |
| 401 | `TOKEN_INVALID` | 서명/키/iss/aud 불일치, role 없음, 계정 무효, 관리자 ver 불일치 | 로그인 화면 |
| 401 | `REFRESH_INVALID` | refresh 없음, 만료, deviceId 불일치 | 저장된 토큰 삭제 후 로그인 화면 |
| 401 | `REFRESH_REUSED` | 재사용 감지로 세션 삭제 | `REFRESH_INVALID`와 동일 |
| 401 | `SOCIAL_TOKEN_INVALID` | 소셜 토큰 검증 실패, nonce 불일치 | 로그인 실패 안내 |
| 401 | `ADMIN_LOGIN_FAILED` | 관리자 아이디 또는 비밀번호 틀림(메시지 통일) | 안내 |
| 403 | `FORBIDDEN` | 권한 부족 | 안내 |
| 403 | `ADMIN_NOT_APPROVED` | 비밀번호는 맞지만 승인 대기 | 안내 |
| 400 | `VALIDATION_FAILED` | Bean Validation 실패 | 개발 버그 |
| 400 | `BAD_REQUEST` | 기존 `IllegalArgumentException` 전반 | 안내 |
| 404 | `DEVICE_NOT_FOUND` | 내 소유가 아닌 기기의 FCM 갱신 | 다시 로그인 |
| 503 | `SOCIAL_PROVIDER_UNAVAILABLE` | 공급자 JWKS 조회 실패 | 잠시 후 재시도 |
| 500 | `INTERNAL_ERROR` | 그 외 | 안내 |

### 9.3 동작 규칙
- 관리자 로그인은 아이디와 비밀번호를 먼저 확인하고, 둘 다 맞을 때만 승인 여부를 알린다.
- **기존 API 동작 변경**: `IllegalArgumentException`이 500에서 400 `BAD_REQUEST`로 바뀐다. 일기, 관리자 등 기존 API에도 적용된다.

## 10. 설정

```properties
jwt.issuer=${JWT_ISSUER}
jwt.member.secret=${JWT_SECRET}
jwt.member.access-token-ttl=${JWT_MEMBER_ACCESS_TTL:30m}
jwt.member.refresh-token-ttl=${JWT_MEMBER_REFRESH_TTL:60d}
jwt.admin.secret=${JWT_ADMIN_SECRET}
jwt.admin.access-token-ttl=${JWT_ADMIN_ACCESS_TTL:2h}

oauth.google.client-ids=${GOOGLE_CLIENT_IDS}            # 쉼표 구분 (iOS 클라이언트 ID, Android server client ID)
oauth.apple.client-id=${APPLE_CLIENT_ID}                 # iOS 번들 ID
oauth.apple.team-id=${APPLE_TEAM_ID}
oauth.apple.key-id=${APPLE_KEY_ID}
oauth.apple.private-key-path=${APPLE_PRIVATE_KEY_PATH}   # .p8 파일 경로 (gitignore 대상)
oauth.apple.token-encryption-key=${APPLE_TOKEN_ENCRYPTION_KEY}
```
- 기존 `JWT_EXPIRATION_ACCESS_TOKEN_MS`, `JWT_EXPIRATION_REFRESH_TOKEN_MS`는 더 이상 쓰지 않는다.
- `JWT_SECRET`과 `JWT_ADMIN_SECRET`은 서로 다른 32바이트 이상의 값이어야 한다. 시작할 때 길이를 검사한다.
- 의존성 추가: `spring-boot-starter-oauth2-resource-server`(oauth2-jose 포함). 애플 client_secret(ES256) 서명은 같은 라이브러리의 Nimbus JOSE로 처리한다.

## 11. 테스트

### 11.1 환경
- 로컬에 Docker가 없으므로 H2 메모리 DB(MySQL 호환 모드)를 쓴다. `src/test/resources/application-test.properties`에서 `ddl-auto=create-drop`으로 둔다.
- Redis, Firebase, OpenAI, S3는 테스트에서 mock 빈으로 대체한다.
- 소셜 토큰은 테스트에서 RSA/EC 키를 직접 만들어 서명한다. JWKS 조회는 테스트용 디코더로 대체해 외부 호출 없이 검증 로직을 그대로 검사한다.
- 애플 code 교환과 revoke HTTP 호출은 인터페이스 뒤에 두고 테스트에서는 fake로 대체한다.

### 11.2 단위 테스트
- refresh 회전: 발급 후 이전 토큰이 무효가 되는지
- 재사용 감지: 60초 이내 직전 토큰은 재발급, 60초 초과는 세션 삭제와 `REFRESH_REUSED`
- 기기 소유자 변경: 덮어쓰기, `prev_refresh_token_hash` 비움, FCM 토큰 이동
- FCM 토큰 유니크: 다른 행의 같은 토큰 정리
- 애플 nonce 비교, 소셜 토큰의 `iss`/`aud`/`exp`/서명 실패 각각
- 애플 refresh 토큰 암호화와 복호화 왕복

### 11.3 통합 테스트 (MockMvc)
- 신규 소셜 로그인은 `isNewMember = true`, 재로그인은 `false`이고 같은 `member_id`인지
- refresh → 로그아웃 → 같은 refresh 사용 시 `REFRESH_INVALID`인지
- 탈퇴 후 같은 소셜 계정으로 로그인하면 새 `member_id`인지, 애플 revoke fake가 호출됐는지
- 권한 규칙
  - 유저 토큰으로 `/admin/**` 호출 시 401 `TOKEN_INVALID` (키가 달라 검증 실패)
  - 관리자 토큰으로 앱 API 호출 시 401
  - role 없는 토큰 거부
  - 만료된 access를 붙인 `/auth/refresh` 정상 동작
- 관리자 로그아웃 후 기존 토큰이 `TOKEN_INVALID`인지
- 관리자 로그인 실패 시 아이디 오류와 비밀번호 오류의 응답이 동일한지
- 에러 응답이 Problem Details 형식이고 `code`가 맞는지

### 11.4 수동 확인 (키와 앱 준비 후)
- 실제 Google ID 토큰(iOS, Android)으로 로그인
- 실제 Apple identity token과 code로 로그인, 애플 refresh 저장 확인, 탈퇴 시 revoke 확인

## 12. 앱/웹 작업 가이드

### 앱
- **deviceId**: 처음 실행할 때 UUID v4를 만들어 보안 저장소에 두고 계속 재사용한다. iOS는 Keychain, Android는 EncryptedSharedPreferences 등을 쓴다.
- **토큰 저장**: access와 refresh 모두 보안 저장소에 둔다.
- **앱 시작**:
  - refresh가 없으면 로그인 화면으로 간다.
  - refresh가 있으면 `/auth/refresh`를 호출한다. 성공하면 홈으로, 실패(`REFRESH_*`)하면 토큰을 지우고 로그인 화면으로 간다.
- **API 401 `TOKEN_EXPIRED`**: refresh를 한 번만 수행(single-flight)한 뒤 원래 요청을 재시도한다. 다른 401 코드는 로그인 화면으로 보낸다.
- **FCM 토큰 갱신 콜백**: `PUT /auth/devices/fcm-token`을 호출한다.
- **로그아웃**: `POST /auth/logout` 후 로컬 토큰을 삭제한다. 네트워크 실패여도 로컬 토큰은 삭제한다.
- **Apple 로그인**: 원본 nonce를 만들어 SHA-256(hex)을 요청에 넣고, 원본 nonce와 identityToken, authorizationCode를 서버로 보낸다.
- **Google 로그인**: Android는 server(web) client ID로 ID 토큰을 요청한다.
- 구버전 앱 강제 업데이트 장치가 필요하다.

### 관리자 웹
- API 경로가 `/admin/**`로 바뀐다(6.3).
- 로그인 응답 바디의 `accessToken`을 메모리나 sessionStorage에 두고 `Authorization` 헤더로 보낸다.
- 401을 받으면 로그인 화면으로 보낸다(refresh 없음).
- 대시보드 등에서 유저가 작성한 텍스트를 HTML로 그대로 렌더링하지 않는다(XSS 방지). 프레임워크 기본 이스케이프를 쓰고 `innerHTML`이나 `v-html` 같은 방식은 피한다.

## 13. 배포 체크리스트
1. 환경변수 추가: `JWT_ADMIN_SECRET`, `GOOGLE_CLIENT_IDS`, `APPLE_CLIENT_ID`, `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY_PATH`, `APPLE_TOKEN_ENCRYPTION_KEY`
2. 필요하면 TTL 변경: `JWT_MEMBER_ACCESS_TTL`, `JWT_MEMBER_REFRESH_TTL`, `JWT_ADMIN_ACCESS_TTL`
3. 사용 중지된 환경변수 정리: `JWT_EXPIRATION_ACCESS_TOKEN_MS`, `JWT_EXPIRATION_REFRESH_TOKEN_MS`
4. Apple `.p8` 키 파일을 서버에 배치(레포에 커밋 금지)
5. `docs/db/2026-10-19-social-login-schema.sql` 실행
6. 새 서버 배포
7. `docs/db/2026-10-19-cleanup-anonymous-members.sql` 실행 및 Redis 키 정리 (소셜 계정이 없는 회원만 대상이라 순서·횟수에 안전)
8. 새 앱(강제 업데이트)과 관리자 웹 배포

## 14. 준비가 필요한 외부 값 (팀)
- Google Cloud: iOS OAuth 클라이언트 ID, Android용 server(web) 클라이언트 ID
- Apple Developer: Team ID, Sign in with Apple용 Key ID와 `.p8` 키, 앱 번들 ID, App ID에 Sign in with Apple capability 활성화
