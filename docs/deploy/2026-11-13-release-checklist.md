# 운영 배포 체크리스트 — 소셜 로그인 + 앱 리디자인 (feat/social-login)

작성일 2026-11-13. 사람이 직접 해야 하는 일만 모았다. 끝낸 항목은 `[x]`로 바꾼다.

## 0. 지금 상태 (2026-11-13 확인)

- **운영 서버는 `main` 코드로 돌고 있다.** `https://piece-of-cake.click/` 과 `/privacy`, `/terms` 가 전부 401 JSON 을 낸다. dev 전용 경로(`POST /admins/login`)도 없다.
- `feat/social-login` 은 main 보다 96커밋 앞서 있고 **원격에 푸시된 적이 없다.**
- 운영 DB 는 main 수준 스키마일 가능성이 높다. 그래서 dev 에서 추가된 스키마까지 같이 올려야 한다(2번).
- OpenAI 계정 크레딧이 소진됐다(`credit_balance_exhausted`). 운영 키가 같은 계정이면 **지금 운영에서도 일기 생성이 실패하고 있다.**

## 1. 코드 올리기

- [ ] `feat/social-login` 푸시 → dev → main 순서로 PR·병합. (Claude 는 푸시하지 않는다)
- [ ] 미추적 백업 `src/main/resources/application-local.properties.bak-2026-10-19` 에 실제 키가 들어 있다. 커밋에 섞이지 않게 하고, 필요 없으면 지운다.

## 2. DB — 새 서버 배포 **전**에 실행

`ddl-auto=validate` 라서 하나라도 빠지면 서버가 뜨지 않는다. 아래 순서를 지킨다. 2026-11-13 에 main 수준 스키마를 임시 DB 로 재현해 이 순서로 실행했고, 결과가 현재 엔티티와 일치하는 것을 확인했다.

- [ ] 운영 DB 백업.
- [ ] 사전 확인: `SHOW CREATE TABLE member;` 에서 `device_id`, `mobile_os`, `preference`, `last_active_at` 이 NULL 허용인지 본다. NOT NULL 이면 `2026-10-19-social-login-schema.sql` 머리말의 `ALTER ... MODIFY` 를 먼저 실행한다.
- [ ] ① `docs/db/2026-11-13-main-to-dev-gap.sql` — `admin` 테이블, `member.created_at`. 이미 있으면 해당 문장만 건너뛴다.
- [ ] ② `docs/db/2026-10-19-social-login-schema.sql` — `social_account`, `member_device`, `admin.token_version`.
- [ ] ③ `docs/db/2026-10-22-member-nickname.sql` — `member.nickname`.
- [ ] ④ `docs/db/2026-10-28-app-redesign-api.sql` — `diary.type`, `diary_color.name`, `music.duration_seconds`, `member.notification_enabled`, `app_version`.
- [ ] **질문 데이터 확인.** 새 코드는 `question_list.category` 로 질문을 뽑는다. 운영 코드(main)는 이 컬럼을 쓰지 않아서 값이 비어 있을 수 있다. 부족하면 문답 화면이 500 이 된다.
  ```sql
  SELECT UPPER(category) AS category, COUNT(*) FROM question_list GROUP BY UPPER(category);
  ```
  - `REQUIRED` 2개 이상, `OPTIONAL` 3개 이상이 **필수**.
  - 건너뛰기 여분 5개를 다 주려면 `OPTIONAL` 8개 이상.
  - 문구가 같은 행이나 빈 행은 서버가 걸러낸다(개수에서 빠진다).
- [ ] 운영 JDBC URL 에 `serverTimezone=Asia/Seoul` 이 있는지 확인. 새 서버는 JVM 타임존을 KST 로 고정하고 DB 의 `DATETIME` 을 KST 벽시계 시각으로 읽는다.

## 3. 환경변수

### 새로 추가 (없으면 기동 실패 또는 로그인 불가)

| 변수 | 값 | 비고 |
|---|---|---|
| `JWT_ADMIN_SECRET` | 32바이트 이상 임의 문자열 | 없으면 기동 실패. `JWT_SECRET` 과 다른 값으로 |
| `APPLE_TOKEN_ENCRYPTION_KEY` | `openssl rand -base64 32` 결과 | 없으면 기동 실패. **한번 정하면 바꾸지 않는다.** 바뀌면 저장된 애플 토큰을 못 풀어 탈퇴 시 폐기가 실패한다. 안전한 곳에 백업 |
| `GOOGLE_CLIENT_IDS` | 웹 클라이언트 ID (`528128213573-…q7ik42.apps.googleusercontent.com`) | iOS·Android 모두 이 ID 를 serverClientId 로 쓴다. 비면 구글 로그인 전부 401 |
| `APPLE_CLIENT_ID` | `com.momeokji.cake` | 번들 ID |
| `APPLE_TEAM_ID` | `X2Z78T337C` | |
| `APPLE_KEY_ID` | `B5HKT2B4W7` | |
| `APPLE_PRIVATE_KEY_PATH` | 서버에 올린 `.p8` 의 절대 경로 | 아래 4번 |

### 값 확인

- [ ] `JWT_SECRET` 이 32바이트 이상인지. 짧으면 새 서버가 기동하지 않는다.
- [ ] `OPENAI_SECRET_KEY` — 새 OpenAI 계정 키로 교체(5번).

### 필요할 때만

- `JWT_MEMBER_ACCESS_TTL`(기본 30m), `JWT_MEMBER_REFRESH_TTL`(60d), `JWT_ADMIN_ACCESS_TTL`(2h)
- `SWAGGER_ENABLED`(기본 false). 켠다면 `SWAGGER_USER`, `SWAGGER_PASSWORD` 를 반드시 같이 넣는다.
- `FCM_DEBUG_ENABLED` 는 운영에서 넣지 않는다(기본 false). 켜면 알림 토글을 무시하는 디버그 푸시 엔드포인트가 열린다.

### 지워도 되는 것 (더 이상 읽지 않음)

- `JWT_EXPIRATION_ACCESS_TOKEN_MS`, `JWT_EXPIRATION_REFRESH_TOKEN_MS`
- `OPENAI_PROMPT_CONTENT_ID`, `OPENAI_PROMPT_SUMMARY_ID`, `OPENAI_PROMPT_COLORS_ID`, `OPENAI_PROMPT_MUSIC_ID`, `OPENAI_PROMPT_SYMPATHY_ID`, `OPENAI_PROMPT_ID`

## 4. 서버에 직접 올리는 파일

- [ ] 애플 로그인 키 `cake-signin-B5HKT2B4W7.p8` 를 서버에 올리고 `APPLE_PRIVATE_KEY_PATH` 로 가리킨다. 레포에 커밋 금지. 애플 콘솔에서 다시 받을 수 없으니 원본을 보관한다.
- [ ] `firebase-service-account.json` 이 빌드에 포함되는지 확인(gitignore 대상이라 빌드 환경에 직접 둬야 한다).

## 5. OpenAI

- [ ] 팀 구글 계정으로 platform.openai.com 가입, 팀원 초대.
- [ ] 크레딧 충전. 자동 충전 + 월 한도 + 알림 메일 설정 권장.
- [ ] 새 계정에서 `gpt-6-luna` 를 쓸 수 있는지, 분당 한도가 충분한지 확인. **이 모델 이름은 아직 실제 호출로 검증된 적이 없다.** 안 되면 `application.properties` 의 `openai.prompts.*.model` 다섯 줄을 바꾼다.
- [ ] 운영용·로컬용 키를 따로 발급해 교체.
- [ ] 운영 전환 뒤 옛 계정의 키 폐기.
- 프롬프트는 코드(`src/main/resources/prompts/`)에 있어서 대시보드에서 옮길 것이 없다. OpenAI 저장 프롬프트 기능은 2026-11-30 에 종료된다. 운영이 그 전에 새 코드로 바뀌지 않으면 **11-30 이후 운영(main)의 일기 생성이 전부 실패한다.**

## 6. 구글 브랜드 인증 (OAuth 동의 화면)

2026-11-13 인증 시도에서 10가지가 실패했다. 원인은 세 묶음이다.

### 6-1. 배포되면 풀리는 것

| 구글이 지적한 것 | 원인 | 조치 |
|---|---|---|
| 홈페이지 URL 이 응답하지 않음 | 운영이 401 JSON 을 냄 | 새 서버 배포 (`GET /` 가 200 HTML) |
| 로그인 페이지가 홈페이지보다 먼저 표시됨 | 위와 같음 | 배포 |
| 홈페이지에 개인정보처리방침 링크가 없음 | 위와 같음 | 배포 (홈에 `/privacy` 링크 있음) |
| 앱 이름("조각케이크")이 홈페이지와 불일치 | 위와 같음 | 배포 (홈 제목이 "조각케이크") |
| 개인정보처리방침 URL 이 응답하지 않음 | 위와 같음 | 배포 |
| 개인정보처리방침 URL 이 홈페이지와 동일 | 둘 다 같은 401 을 냄 | 배포 |

### 6-2. 배포해도 **안 풀리는 것** — 서버 코드와 문서 내용을 고쳐야 한다

| 구글이 지적한 것 | 원인 | 조치 |
|---|---|---|
| 개인정보처리방침 형식이 잘못됨 | 지금 `/privacy` 는 S3 의 **PDF 로 302**. 구글은 PDF 를 처리하지 못하고, "전용 웹 페이지 본문의 HTML" 을 요구한다 | `/privacy` 가 우리 도메인에서 방침 전문을 **HTML 로 직접** 내도록 서버 수정 |
| 개인정보처리방침이 내 도메인에 없음(연관성) | PDF 가 `amazonaws.com` 에 있음. 구글은 홈페이지와 같은 도메인 또는 소유 확인된 도메인을 요구한다 | 위와 같음 |
| 개인정보처리방침에 충분한 내용이 없음 | 현재 방침은 **옛 익명 앱 기준**이다("별도의 회원가입 없이 사용", 수집 항목이 FCM 토큰). 구글·애플 로그인, 이메일, 닉네임 언급이 없다. 구글은 구글 사용자 데이터를 무엇을 받고, 어떻게 쓰고·보관하고·공유하는지 밝히라고 요구한다 | **방침 문구 개정**(사람·법무 판단). 반영할 사실은 6-4 참고 |
| 홈페이지에 앱 목적 설명이 없음 | 지금 홈은 소개가 한 줄이다. 구글은 기능 전체 설명과 "왜 구글 사용자 데이터를 요청하는지"를 요구한다 | 홈 HTML 보강(서버 수정) |

- [ ] `/privacy`, `/terms` 를 HTML 페이지로 바꾸는 서버 수정. (Claude 가 할 수 있다. 방침 문구가 확정돼야 최종본이 된다)
- [ ] 홈페이지 문구 보강. (Claude 가 할 수 있다)
- [ ] 개정된 개인정보처리방침·이용약관 문구 확정. (사람)
- [ ] 이용약관도 같은 이유로 옛 기준일 수 있다. 같이 검토.

### 6-3. 구글 콘솔에서 사람이 하는 것

- [ ] **도메인 소유권 확인.** "웹사이트가 나에게 등록되어 있지 않습니다" 지적. Google Search Console 에서 `piece-of-cake.click` 을 **GCP 프로젝트 소유자 계정으로** 확인한다. 2026-11-13 기준 이 도메인에 TXT 레코드가 하나도 없다. DNS 에 구글이 주는 TXT 레코드를 추가하는 방식이 가장 확실하다. 확인 후 **24시간 기다렸다가** 재시도하라고 구글이 안내한다.
- [ ] 동의 화면의 홈페이지 `https://piece-of-cake.click`, 개인정보처리방침 `https://piece-of-cake.click/privacy`, 서비스 약관 `https://piece-of-cake.click/terms`, 승인된 도메인 `piece-of-cake.click` 확인.
- [ ] 배포 + 6-2 + 소유권 확인이 **모두 끝난 뒤에** 재제출. 구글이 "모든 문제를 해결한 뒤에만 다시 제출하라"고 안내한다.
- [ ] 제출 전 직접 확인:
  ```bash
  curl -s -o /dev/null -w "%{http_code} %{content_type}\n" https://piece-of-cake.click/
  curl -s -o /dev/null -w "%{http_code} %{content_type}\n" https://piece-of-cake.click/privacy
  curl -s -o /dev/null -w "%{http_code} %{content_type}\n" https://piece-of-cake.click/terms
  ```
  셋 다 `200 text/html` 이어야 한다(리다이렉트 없이).

### 6-4. 개인정보처리방침에 반영할 사실 (현재 서버 동작)

- 가입 방식: 구글 로그인, 애플 로그인. 익명(기기 기반) 사용은 없어졌다.
- 구글에서 받는 것: ID 토큰의 사용자 식별자(`sub`)와 이메일. 서버는 식별자로 회원을 구분하고, 이메일은 참고용으로만 저장한다. 그 외 구글 데이터(연락처, 드라이브 등)는 요청하지 않는다.
- 애플에서 받는 것: 사용자 식별자, 이메일(첫 로그인 때만, 가리기 주소일 수 있음), 탈퇴 시 연결 해제에 쓰는 토큰(암호화 저장).
- 그 외 저장: 닉네임, 기기 식별용 UUID, 푸시 토큰(FCM), 알림 수신 여부, 일기 본문·사진, AI 분석 결과(요약·색상·음악·공감 메시지).
- 제3자 전송: 일기 본문이 분석을 위해 OpenAI(미국)로 전송된다. 사진은 AWS S3(서울), 푸시는 Firebase.
- 보관·삭제: 탈퇴하면 즉시 비활성화되고, 3일이 지난 뒤 삭제 스케줄러(3일마다 새벽 2시 실행)가 DB 와 사진에서 완전히 지운다. 즉 탈퇴 후 3~6일 사이에 삭제된다. 애플 계정은 탈퇴 시 연결 해제(토큰 폐기)를 호출한다.

## 7. 배포 **직후**

- [ ] 기동 로그에 에러가 없는지, `GET /health` 200.
- [ ] `docs/db/2026-10-19-cleanup-anonymous-members.sql` 실행. **기존 익명 회원과 그 일기·사진을 전부 삭제 대기로 돌리는 되돌리기 어려운 작업**이다. 파일 머리말의 건수 확인 쿼리를 먼저 돌린다. 3~6일 뒤 스케줄러가 완전히 지운다.
- [ ] 운영 Redis 의 옛 회원 키 정리. 같은 파일 끝의 `redis-cli --scan --pattern 'aidiary:prod:refset:*'` 주석 참고.
- [ ] 최초 슈퍼 관리자 만들기. `POST /admin/auth/signup` 으로 가입 후 DB 에서 직접 승인:
  ```sql
  UPDATE admin SET is_valid = 1, is_super = 1 WHERE admin_id = '<가입한 ID>';
  ```
- [ ] 앱 최소 버전 등록: `PUT /admin/app/versions/IOS`, `PUT /admin/app/versions/AND`. 등록 전에는 `/app/min-version` 이 null 을 주고 앱은 "강제 업데이트 없음"으로 처리한다. 구버전 앱은 새 서버와 호환되지 않으므로 스토어 배포 일정에 맞춰 넣는다.
- [ ] 음악 길이 채우기: `YOUTUBE_API_KEY=... scripts/backfill-music-duration.sh` 가 내는 SQL 을 운영에 실행. 안 하면 재생 시간이 null 로 나간다.
- [ ] 실기기로 한 바퀴: 구글 로그인 → 문답 일기(건너뛰기 포함) → 확정(색상 이름·음악) → 애플 로그인 → 탈퇴. 탈퇴 때 서버 로그에 "애플 토큰 revoke 실패" ERROR 가 없어야 한다.
- [ ] 공감 메시지 스케줄러가 한 번 돈 뒤 푸시가 오는지.

## 8. 앱·관리자 웹과 맞출 것

- [ ] 구버전 앱은 새 서버에서 동작하지 않는다(익명 가입·로그인 API 삭제). 새 앱 배포와 강제 업데이트 시점을 서버 배포와 맞춘다.
- [ ] 관리자 웹: API 경로가 `/admin/**` 로 바뀌었다(예: `/admins/login` → `/admin/auth/login`). 토큰은 응답 바디의 `accessToken` 을 `Authorization` 헤더로 보낸다.
- [ ] 앱에 전달했는지 확인:
  - 문답 건너뛰기 변경 — `docs/app-notes/2026-11-12-question-skip.md`
  - 캘린더 기본 정렬 `recent`, `stats.dailyLimit` 추가
  - 색상 응답이 `{name, hex}` 객체 배열
  - 401 은 `TOKEN_EXPIRED`(재발급 후 재시도)와 `TOKEN_INVALID`(로그인 화면) 구분
  - 관리자 전체 공지는 알림 토글과 무관하게 발송
  - FCM 메시지 구성(notification + data, Android 채널, APNs 헤더)

## 9. 아직 실제로 검증하지 못한 것

배포 전에 로컬에서 먼저 확인하면 좋다.

- OpenAI 실호출 전부(초안·요약·색상·음악·공감). 크레딧 소진으로 막혀 있었다. 모델 이름과 색상 응답 형식이 여기서 처음 검증된다.
- 애플 로그인과 탈퇴 시 토큰 폐기(실기기 필요). `.p8` 키로 서명이 되는 것까지만 확인했다.
- 건너뛰기 여분 질문의 앱 연동.
- 이미지 첨부. 로컬에서도 운영 S3 버킷에 올라가므로 테스트 사진이 섞인다.
