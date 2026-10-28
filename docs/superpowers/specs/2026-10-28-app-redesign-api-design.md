# 앱 리디자인 대응 API 설계

작성일: 2026-10-28
브랜치: `feat/app-redesign-api` (`feat/social-login`에서 분기, 완료 후 `feat/social-login`으로 병합)
선행 스펙: `docs/superpowers/specs/2026-10-19-social-login-design.md`

## 1. 배경과 목적

앱이 소셜 로그인 도입과 화면 리디자인을 한 번에 진행하면서 서버에 16개 항목을 요청했고, 추가로 최소 버전 엔드포인트를 부탁했다. 앱팀과 항목별로 합의를 마쳤고 이 문서는 그 합의를 서버 관점의 설계로 옮긴 것이다.

- 기존 유저와 기존 일기는 버리기로 했다. 구 앱 호환은 고려하지 않는다.
- 서버를 먼저 배포하고 앱이 뒤따른다. 소셜 로그인과 이번 변경은 `feat/social-login` 한 덩어리로 dev에 들어간다.
- 앱은 한국 사용자 전용이다. 날짜 경계는 모두 `Asia/Seoul`(KST) 기준이다.

## 2. 목표 / 비목표

### 목표
- 앱 화면이 요구하는 필드와 엔드포인트를 모두 제공한다(3장).
- 날짜 경계에 걸리는 기능(통계, 일일 제한, 캘린더)이 서버 타임존과 무관하게 KST로 일관되게 동작한다.
- 에러 코드 목록을 스웨거에서 볼 수 있게 한다.

### 비목표
- Android Apple 로그인. Android는 Google만 쓴다. 서버 변경 없음.
- 닉네임 중복 검사. 닉네임은 유니크하지 않으며 실시간 검사 엔드포인트를 만들지 않는다.
- 신규 회원 닉네임 자동 채움. 서버는 `nickname`을 null로 두고 앱이 SDK에서 받은 이름을 입력창에 미리 채운다.
- 구 앱 호환. 응답 형식이 깨지는 변경(페이지네이션, 색상)을 그대로 한다.

### 안티 목표
- 기존 일기 행을 살리려고 마이그레이션 로직을 복잡하게 만드는 것.
- 앱이 아직 없는 상태에서 실기기 검증을 가정한 코드를 넣는 것.

## 3. 변경 목록 (앱팀 합의 번호 기준)

### 16. 타임존 (전제)
- JVM 기본 타임존을 `Asia/Seoul`로 고정한다. `AiDiarybackendApplication`의 static 초기화 블록에서 `TimeZone.setDefault`를 호출한다. 테스트 컨텍스트도 같은 클래스를 로드하므로 동일하게 적용된다.
- `ClockConfig`의 `Clock`을 `Clock.system(ZoneId.of("Asia/Seoul"))`로 바꾼다. 날짜 경계를 쓰는 서비스는 이 `Clock`으로 "오늘"을 구한다.
- `spring.jackson.time-zone=Asia/Seoul`을 `application.properties`에 둔다.
- 일기 응답의 `createdAt`은 `OffsetDateTime`으로 내려 `2025-09-16T21:12:00+09:00` 형식이 된다. 변환은 `LocalDateTime.atZone(KST).toOffsetDateTime()`이다. 대상 DTO: `DiaryConfirmResponseDto`, `DiaryDetailResponseDto`, `DiaryListResponseDto`.
- DB 컬럼은 그대로 `DATETIME`(KST 벽시계 시각)이다. 운영 JDBC URL의 `serverTimezone=Asia/Seoul`이 유지되어야 한다. **운영 JVM과 JDBC URL 타임존 확인은 사람이 한다.**

### 1. 일기 타입
- `diary.type VARCHAR(10) NOT NULL DEFAULT 'FREE'`. 값은 `FREE`, `QNA`. 엔티티는 `DiaryType` enum을 `@Enumerated(STRING)`으로 매핑한다.
- `POST /diaries`(문답 확정)는 `QNA`, `POST /diaries/free`는 `FREE`로 저장한다.
- `DiaryDetailResponseDto`, `DiaryListResponseDto`, `DiaryConfirmResponseDto`에 `type` 필드를 넣는다.

### 2. 마이페이지 통계
- `GET /users/me/stats` (USER)
- 응답: `{ "totalCount": 12, "monthCount": 3, "streakDays": 2, "todayCount": 1, "dailyLimit": 3 }`
  - `totalCount`: 유효한(`is_valid=1`) 일기 전체 개수
  - `monthCount`: KST 기준 이번 달 1일 00:00 이상, 다음 달 1일 00:00 미만
  - `streakDays`: 연속 기록 일수. 오늘 기록이 있으면 오늘부터, 없으면 어제부터 거슬러 올라가며 하루라도 비면 멈춘다. 어제도 없으면 0.
  - `todayCount`: KST 기준 오늘 00:00 이상, 내일 00:00 미만
  - `dailyLimit`: 6번의 제한 값. 앱팀 요청 필드는 아니지만 앱이 상수를 복제하지 않도록 함께 준다. (작성자의 추가)
- 연속 일수는 `SELECT DISTINCT DATE(created_at)`를 네이티브 쿼리로 가져와 자바에서 센다. 회원 하루 최대 3개라 행 수가 작다.
- 소프트 삭제된 일기는 어떤 통계에도 들어가지 않는다.

### 3. 로그인 수단
- `GET /users/me` 응답에 `provider` 필드. 값은 `GOOGLE` / `APPLE` 대문자 그대로. `social_account`에서 `member_id`로 찾아 첫 행의 provider다. 행이 없으면 null(방어용).

### 4. 알림 수신 on/off
- `member.notification_enabled TINYINT NOT NULL DEFAULT 0`. 기본값 꺼짐. `Member.create`도 false로 만든다.
- `GET /users/me` 응답에 `notificationEnabled`.
- `PUT /users/me/notification` 요청 `{ "enabled": true }` → 200 `{ "notificationEnabled": true }`. `enabled`는 `@NotNull`.
- 토글 하나가 리마인드 알림과 공감(시식평) 알림을 함께 제어한다.
  - `DiaryRemindScheduler`: 대상 회원을 `isValid && notificationEnabled`로 조회한다.
  - `DiarySympathyScheduler`: 공감 메시지 생성은 그대로 하고, 푸시 발송 전에 회원의 `notificationEnabled`를 확인해 꺼져 있으면 보내지 않는다.
  - 관리자 전체 공지(`FcmService.sendNotificationToAllMembers`)는 이 토글의 영향을 받지 않는다. 서비스 공지는 기록 알림이 아니기 때문이다. (작성자의 판단)

### 5. 닉네임
- 규칙은 현행 유지: `^[가-힣A-Za-z0-9]{2,10}$`. 완성형 한글·영문·숫자 2~10자, 공백·자모·이모지·특수문자 불가. 중복 허용.
- 위반 시 400 `VALIDATION_FAILED`.
- 스웨거 `PUT /users/me/nickname` 설명에 정규식을 그대로 적어 앱이 복사해 쓰게 한다.

### 6. 일일 작성 제한
- 하루 3개. 값은 `diary.daily-limit` 프로퍼티(기본 3).
- KST 기준 오늘 유효한 일기 개수가 제한 이상이면 429 `DIARY_DAILY_LIMIT_EXCEEDED`.
- 검사 위치: `POST /diaries/qna`(생성 요청, OpenAI 호출 전), `POST /diaries`(문답 확정), `POST /diaries/free`(자유 확정). 확정 시 검사는 저장 직전이 아니라 OpenAI 호출 전에 한다. 비용이 큰 호출을 먼저 하지 않기 위해서다.
- 동시 요청으로 1개가 초과될 수 있는 경쟁 조건은 허용한다. 잠금을 걸지 않는다.
- 조회는 별도 엔드포인트 없이 2번 `stats.todayCount`로 한다.

### 7. 리스트 정렬
- `GET /diaries`와 `GET /diaries/calendar` 모두 `sort` 파라미터를 받는다. 허용값 `recent`(최신순, 기본), `oldest`(오래된순). `created`는 `oldest`의 별칭으로 유지한다. 그 외 값은 400 `BAD_REQUEST`.
- `/diaries/calendar`의 기본 정렬이 오래된순에서 `recent`로 바뀐다. 두 엔드포인트의 기본값을 맞추기 위해서다. (작성자의 판단)
- 정렬 해석은 `DiarySort` enum 하나에 모아 두 엔드포인트가 공유한다.

### 8. 페이지네이션 메타
- `GET /diaries` 응답이 배열에서 객체로 바뀐다.
  ```json
  { "items": [ ...DiaryListResponseDto ], "page": 0, "size": 10, "hasNext": true, "totalCount": 23 }
  ```
- `/diaries/calendar`는 월 단위 전체를 주므로 배열 그대로 둔다.

### 9. 색상 이름
- `diary_color.name VARCHAR(30) NULL`. 엔티티 필드 `name`. 기존 `color_name`(hex) 컬럼과 필드는 이름을 바꾸지 않는다(validate 호환).
- 응답의 `colors`가 `[{ "name": "새싹 연두", "hex": "#C3E9A6" }]` 형태가 된다. 대상: `DiaryConfirmResponseDto`, `DiaryDetailResponseDto`, `DiaryListResponseDto`. `name`은 null일 수 있다.
- `DiaryColorService.parseColors`는 다음 셋을 모두 받는다. 코드펜스는 먼저 벗긴다.
  1. `{"colors":[{"name":"새싹 연두","hex":"#C3E9A6"}, ...]}` → name, hex
  2. `{"colors":"#AABBCC, #DDEEFF"}` → name null
  3. hex가 섞인 임의 문자열 → name null
  hex는 대문자로 정규화한다. hex가 하나도 없으면 예외(현행 유지).
- **OpenAI 색상 프롬프트를 1번 형식을 내도록 바꾸는 작업은 OpenAI 대시보드에서 사람이 한다.** 바꾼 뒤 `openai.prompts.colors.version`을 올린다. 프롬프트가 바뀌기 전까지 서버는 2·3번 형식으로 동작하며 name이 null이다.

### 10. 음악 길이
- `music.duration_seconds INT NULL`. 엔티티 필드 `durationSeconds`.
- `MusicDto`(Detail, List, Confirm)와 관리자 `MusicResponse`에 `durationSeconds` 추가. null 허용.
- 관리자 `POST /admin/musics`, `PATCH /admin/musics/{id}` 요청에 선택 필드 `durationSeconds`(`@Positive`).
- 기존 행 백필은 `scripts/backfill-music-duration.sh`로 한다. `YOUTUBE_API_KEY`를 받아 `videos.list(part=contentDetails)`를 호출하고 ISO 8601 duration을 초로 바꿔 `UPDATE music SET duration_seconds=... WHERE video_id='...'` SQL을 표준 출력으로 낸다. 서버 런타임에 유튜브 API 의존을 넣지 않는다. **스크립트 실행은 사람이 한다.**

### 11. 문답 건너뛰기
- 변경 없음. `target_set`의 각 항목은 `question`, `answer` 모두 `@NotBlank`. 답이 없는 문항은 앱이 빼고 보내고, 전부 건너뛰면 앱이 생성 요청을 하지 않는다. 스웨거 설명에 적는다.

### 13. 에러 코드
- `ErrorCode`에 추가:
  - `DIARY_NOT_FOUND` (404): 내 일기가 아니거나 없거나 삭제됨
  - `DIARY_DAILY_LIMIT_EXCEEDED` (429): 일일 작성 제한 초과
- `DiaryService`의 `getDetail`, `deleteDiary`, `updateContent`에서 일기를 못 찾으면 `BusinessException(DIARY_NOT_FOUND)`을 던진다. 기존 400 `BAD_REQUEST`에서 404로 바뀐다.
- 스웨거 문서 설명(`OpenApiConfig`)에 전체 에러 코드 표를 넣는다. 선행 스펙 9.2절 표에 위 둘을 더한 것이다. `TOKEN_EXPIRED`(access 만료)와 `TOKEN_INVALID`(서명·iss·aud 불일치, 탈퇴한 회원 등)는 서로 다른 코드임을 명시한다.

### 15. Apple revoke
- 변경 없음. 이미 `AuthService.withdraw`가 처리한다.

### 17. 최소 버전
- 테이블 `app_version`:
  ```sql
  platform       VARCHAR(10)  NOT NULL PRIMARY KEY  -- IOS, AND
  min_version    VARCHAR(20)  NOT NULL
  latest_version VARCHAR(20)  NULL
  store_url      VARCHAR(500) NULL
  updated_at     DATETIME     NOT NULL
  ```
- `GET /app/min-version` (공개, 토큰 불필요)
  ```json
  {
    "ios":     { "minVersion": "1.2.0", "latestVersion": "1.3.0", "storeUrl": "https://apps.apple.com/..." },
    "android": { "minVersion": "1.2.0", "latestVersion": null,    "storeUrl": null }
  }
  ```
  행이 없는 플랫폼은 그 키의 값이 null이다. 버전 비교는 앱이 한다.
- `PUT /admin/app/versions/{platform}` (ADMIN, SUPER_ADMIN). `platform`은 `IOS` 또는 `AND`. 요청 `{ "minVersion": "1.2.0", "latestVersion": "1.3.0", "storeUrl": "..." }`. upsert. `minVersion`은 필수이고 `^\d+\.\d+\.\d+$`, `latestVersion`도 같은 형식이되 선택. 응답 200에 저장된 값.
- `SecurityConfig` 앱 체인의 공개 경로에 `GET /app/min-version`을 추가한다.
- 배포 순서 권고(앱팀에 전달): 이 엔드포인트가 포함된 서버가 먼저 나가고, 그것을 읽는 앱 버전이 퍼진 뒤에야 다음 깨지는 변경에서 최소 버전을 올릴 수 있다. 이번 배포에서는 기존 유저를 버리므로 순서 문제가 없다.

## 4. 데이터 모델 변경 요약

`docs/db/2026-10-28-app-redesign-api.sql` (새 서버 배포 **전**에 실행):

```sql
ALTER TABLE diary       ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'FREE';
ALTER TABLE member      ADD COLUMN notification_enabled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE diary_color ADD COLUMN name VARCHAR(30) NULL;
ALTER TABLE music       ADD COLUMN duration_seconds INT NULL;
CREATE TABLE app_version (...);
```

## 5. API 변경 요약

| 메서드 | 경로 | 변경 | 인증 |
|---|---|---|---|
| GET | `/users/me` | `provider`, `notificationEnabled` 추가 | USER |
| GET | `/users/me/stats` | 신규 | USER |
| PUT | `/users/me/notification` | 신규 | USER |
| GET | `/diaries` | 객체 응답, `sort=recent\|oldest\|created` | USER |
| GET | `/diaries/calendar` | `sort` 추가, 기본 `recent` | USER |
| GET | `/diaries/{id}` | `type`, `colors[].name`, `music.durationSeconds`, `createdAt` 오프셋, 404 | USER |
| POST | `/diaries/qna` | 429 일일 제한 | USER |
| POST | `/diaries`, `/diaries/free` | `type`, colors 객체, 429 | USER |
| DELETE / PATCH | `/diaries/{id}` | 404 `DIARY_NOT_FOUND` | USER |
| GET | `/app/min-version` | 신규 | 공개 |
| PUT | `/admin/app/versions/{platform}` | 신규 | ADMIN |
| POST / PATCH | `/admin/musics` | `durationSeconds` 선택 | ADMIN |

## 6. 테스트

- 통합 테스트(H2, MockMvc)로 각 엔드포인트의 응답 형식과 에러 코드를 확인한다. 기존 `IntegrationTestSupport`를 쓴다. OpenAI·S3는 mock 한다.
- 날짜 경계 테스트는 `Clock`을 고정해 자정 전후를 검증한다. 일기 `createdAt`은 `@CreationTimestamp`라 테스트에서 직접 넣어야 하므로 `Diary.builder().createdAt(...)`로 저장한다.
- `DiaryColorService` 파서는 세 가지 입력 형식 단위 테스트.
- 스케줄러는 기존 방식대로 mock 기반 단위 테스트.

## 7. 사람이 해야 하는 일

1. 운영 JVM 타임존과 JDBC URL `serverTimezone` 확인.
2. `docs/db/2026-10-28-app-redesign-api.sql` 운영 실행(배포 전).
3. OpenAI 색상 프롬프트를 `{"colors":[{"name","hex"}]}` 형식으로 변경하고 `openai.prompts.colors.version` 갱신.
4. `scripts/backfill-music-duration.sh`로 음악 길이 백필.
5. 관리자 API로 `app_version` 초기값 등록.
6. 앱팀에 전달: 스웨거 갱신 내용, 정렬 기본값 변경, `stats.dailyLimit` 추가, 관리자 공지는 토글 무관.
