# 약관 목록·동의 기록을 서버에서 관리

작성일: 2026-11-27
브랜치: `feat/terms-consent` (서버). 앱은 이 저장소 작업에 포함하지 않는다. 앱 세션에 전달문으로 넘긴다(5절이 그 내용이다).

## 1. 왜 하는가

스토어 심사 전에 "AI 일기 생성을 위한 개인정보 국외 이전 동의"를 가입 시 필수 항목으로 추가해야 한다
(Apple App Review Guideline 5.1.2(i): 개인정보를 외부 AI 로 보내기 전에 앱 안에서 명시적 동의를 받는다).

지금은 약관 항목 3개가 앱(`lib/presentation/login/components/terms_sheet.dart`)에 하드코딩돼 있고,
서버에는 약관 API 도, 필수 동의 검증도, 동의 이력도 없다. 동의 여부는 앱 메모리(`LoginViewModel._termsAgreed`)에만 있다.

항목을 하나 더 하드코딩하는 대신 이번에 구조를 바꾼다. 나중에 관리자 페이지에서 약관과 동의 현황을 관리할 것이기 때문이다.

## 2. 목표 / 하지 않는 것 / 해서는 안 되는 것

**목표**
- 앱의 약관 시트가 서버가 내려준 목록(제목, 필수 여부, 순서, 본문 링크)만으로 그려진다. 앱에는 약관 제목이나 종류가 박혀 있지 않다.
- "전체 동의하기"와 "동의하고 시작하기" 활성화가 서버의 필수 여부 값으로 계산된다.
- 서버가 신규 가입 때 필수 약관 동의를 검증하고, 회원별로 어떤 약관(버전 포함)에 언제 동의했는지 저장한다.
- "기록 알림 받기"(선택)도 같은 목록의 한 줄로 내려가고, 동의하면 가입 시점에 알림 설정이 켜진다.

**하지 않는 것**
- 관리자용 약관 API 와 화면. 스키마만 관리자 페이지가 읽고 쓸 수 있게 만든다.
- 약관 본문을 DB 나 관리자 페이지에서 편집하는 것. 본문은 지금처럼 `classpath:site/*.html` 이다.
- 약관 버전이 올라갔을 때 기존 회원에게 재동의를 받는 흐름. 지금은 기존 회원이 없다(사용자 확인, 2026-11-27).
- 로그인된 기존 세션에 동의 화면을 띄우는 것(같은 이유).
- 마이페이지 알림 토글의 동작 변경. 가입 후 켜고 끄는 것은 설정 값만 바꾸고 동의 기록은 건드리지 않는다.

**해서는 안 되는 것**
- 필수 약관 동의 기록이 없는 회원이 만들어지는 것.
- 서버 목록을 못 받았을 때 앱이 옛 하드코딩 목록으로 대신 그리거나, 목록 없이 로그인을 진행시키는 것.
- 방침 5.1 과 `/ai-consent` 본문이 서로 다른 사실을 말하는 것(이미 `SitePagesIntegrationTest` 가 지킨다).

## 3. 검토한 방법

| | 방법 | 장단점 |
|---|---|---|
| **A (선택)** | DB 테이블 + 로그인 요청에 동의한 약관 ID 를 함께 보냄 | 가입과 동의 기록이 한 트랜잭션이라 동의 없는 회원이 생길 수 없다. 관리자 페이지는 테이블만 다루면 된다. 운영 DB 에 SQL 을 먼저 실행해야 하고, 로그인 요청 형식이 바뀐다. |
| B | DB 테이블 + 가입 후 별도 API 로 동의 전송 | 로그인 API 는 그대로다. 전송이 실패하면 동의 없는 회원이 남고, 서버가 필수 여부를 강제할 수 없다. |
| C | 목록을 서버 코드에 고정 | DB 변경이 없다. 약관을 바꾸려면 배포해야 해서 관리자 페이지 목적과 맞지 않는다. |

사용자가 A 를 골랐다. 알림 항목까지 서버 목록에 넣는 것도 사용자가 확인했다("작업량 생각하지 말고 올바른 쪽으로").

## 4. 서버 설계

### 4.1 테이블

```sql
CREATE TABLE terms (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(40)  NOT NULL,   -- SERVICE, PRIVACY, AI_OVERSEAS_TRANSFER, RECORD_NOTIFICATION
    version       INT          NOT NULL,   -- 같은 code 안에서 1부터
    title         VARCHAR(100) NOT NULL,   -- 시트에 보이는 문구
    required      TINYINT      NOT NULL,
    display_order INT          NOT NULL,
    content_path  VARCHAR(200) NULL,       -- 본문 페이지 경로(/terms 등). 본문이 없으면 NULL
    is_active     TINYINT      NOT NULL,   -- 지금 가입 화면에 내보내는 행인지
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_terms_code_version (code, version)
);

CREATE TABLE member_terms_agreement (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  VARCHAR(255) NOT NULL,
    terms_id   BIGINT       NOT NULL,
    agreed_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_terms (member_id, terms_id),
    KEY idx_member_terms_member (member_id)
);
```

- 약관을 개정하면 같은 `code` 로 `version` 을 올린 새 행을 만들고 옛 행의 `is_active` 를 끈다. 동의 기록은 `terms_id` 를 가리키므로 어느 버전에 동의했는지가 남는다.
- 초기 데이터(모두 version 1, active):

| display_order | code | title | required | content_path |
|---|---|---|---|---|
| 1 | SERVICE | 서비스 이용약관 | 1 | /terms |
| 2 | PRIVACY | 개인정보 수집 및 이용 | 1 | /privacy |
| 3 | AI_OVERSEAS_TRANSFER | AI 일기 생성을 위한 개인정보 국외 이전 동의 | 1 | /ai-consent |
| 4 | RECORD_NOTIFICATION | 기록 알림 받기 | 0 | NULL |

- SQL 은 `docs/db/2026-11-27-terms-consent.sql` 에 테이블 생성과 초기 데이터를 함께 둔다. 실행 시점은 새 서버 배포 전이다(`ddl-auto=validate`).

### 4.2 약관 목록 API

`GET /auth/terms` (공개, 토큰 불필요. `/terms` 는 HTML 페이지라 쓸 수 없다)

```json
{
  "terms": [
    {"id": 1, "title": "서비스 이용약관", "required": true, "contentPath": "/terms"},
    {"id": 2, "title": "개인정보 수집 및 이용", "required": true, "contentPath": "/privacy"},
    {"id": 3, "title": "AI 일기 생성을 위한 개인정보 국외 이전 동의", "required": true, "contentPath": "/ai-consent"},
    {"id": 4, "title": "기록 알림 받기", "required": false, "contentPath": null}
  ]
}
```

- `is_active` 인 행을 `display_order` 순으로 준다.
- `code` 는 응답에 넣지 않는다. 앱이 종류별로 분기하지 않게 하기 위해서다(내 판단).
- `contentPath` 는 서버 기준 상대 경로다. 앱이 API 기본 주소에 붙여 연다. 테스트 서버에서도 그 서버의 페이지가 열린다(내 판단).

### 4.3 로그인 요청과 가입 처리

`POST /auth/social/google`, `POST /auth/social/apple` 요청에 선택 필드를 추가한다.

```json
{ "...": "기존 필드", "agreedTermsIds": [1, 2, 3, 4] }
```

처리 순서(`SocialLoginService`):

1. 소셜 토큰 검증(기존).
2. 소셜 계정 조회. **있으면** 기존 회원이다. `agreedTermsIds` 는 무시한다(기록하지 않고 알림 설정도 바꾸지 않는다).
3. **없으면** 신규 가입이다.
   - 활성 약관 중 필수인 것이 `agreedTermsIds` 에 모두 있어야 한다. 하나라도 빠지면 400 `TERMS_AGREEMENT_REQUIRED` 를 주고 회원을 만들지 않는다. 필드가 없거나 빈 배열이어도 같다.
   - 활성 필수 약관이 하나도 없으면(초기 데이터를 넣지 않았거나 전부 꺼진 경우) 가입을 거절한다. 검증이 빈 조건으로 통과해 동의 없는 회원이 생기는 것을 막기 위해서다(내 판단). 응답은 503 `TERMS_NOT_CONFIGURED` 이고 ERROR 로그를 남긴다.
   - 활성 약관이 아닌 ID(모르는 ID, 꺼진 버전)는 조용히 버린다.
   - 회원, 소셜 계정, 동의 기록(`agreed_at` 은 서버 시각)을 한 트랜잭션(`SocialAccountRegistrar.register`)으로 만든다.
   - 동의한 항목에 `RECORD_NOTIFICATION` 이 있으면 `member.notification_enabled` 를 켠 채로 만든다.
4. 동시 첫 로그인으로 유니크 위반이 나면 지금처럼 상대가 만든 계정을 기존 회원으로 쓴다(동의 기록은 먼저 성공한 요청의 것이 남는다).

`ErrorCode` 에 `TERMS_AGREEMENT_REQUIRED(400, "필수 약관에 동의해야 가입할 수 있습니다.")` 와
`TERMS_NOT_CONFIGURED(503, "약관 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.")` 를 추가한다.

로그아웃했다가 다시 로그인하는 기존 회원도 앱에서는 시트를 다시 보고 체크한다(앱은 소셜 로그인 전에 신규인지 알 수 없다. 지금도 그렇다).
서버는 그 값을 버리므로 이때 "기록 알림 받기"를 체크해도 알림 설정은 바뀌지 않는다. 설정은 마이페이지에서 바꾼다.

운영·테스트 DB 에 이미 있는 개발용 계정은 동의 기록 없이 계속 로그인된다. 채워 넣거나 지우지 않는다.

### 4.4 탈퇴

탈퇴 시 그 회원의 `member_terms_agreement` 행을 지운다. 방침 4항이 "탈퇴 후에는 회원 번호와 가입·탈퇴 일시만 남는다"고 약속하기 때문이다(내 판단. 동의 증빙을 탈퇴 후에도 보관하려면 방침 문구를 먼저 바꿔야 한다).

### 4.5 그대로 두는 것

- `PUT /users/me/notification`: 마이페이지 토글용으로 남긴다. 설명의 "앱은 약관 체크값을 가입 직후 이 API 로 보낸다"는 문장만 고친다.
- `/terms`, `/privacy`, `/ai-consent` 페이지(이미 이 브랜치에 반영).

### 4.6 테스트

- 목록 API: 토큰 없이 200, 활성 행만 순서대로, 꺼진 버전 제외.
- 신규 가입: 필수 누락/필드 없음 → 400 이고 회원·소셜 계정이 안 생김. 전부 동의 → 회원과 동의 기록 생성. 알림 항목 동의 여부에 따라 `notificationEnabled`.
- 기존 회원 로그인: `agreedTermsIds` 없이도 200, 기록 변화 없음.
- 탈퇴 후 동의 기록 없음.
- 통합 테스트는 매 테스트 뒤 모든 테이블을 비우므로, `IntegrationTestSupport` 가 테스트 전에 초기 약관을 심고 `loginAsMember` 가 필수 약관 ID 를 보내도록 바꾼다.

## 5. 앱에 전달할 내용 (`a-piece-of-cake-app`)

앱은 피그마를 보는 별도 세션이 구현한다. 아래는 서버 계약에서 따라 나오는 요구이고, 화면 구성과 클래스 설계는 앱 쪽이 정한다.

- 약관 목록을 받는 API·모델·리포지토리를 추가한다(`GET /auth/terms`, 인증 헤더 없음).
- `TermsSheet` 는 받은 목록으로 그린다.
  - 줄마다 `[필수]`/`[선택]`, 제목, `contentPath` 가 있으면 보기 버튼(`API 기본 주소 + contentPath` 를 연다).
  - "전체 동의하기"는 모든 줄, 버튼 활성화는 필수인 줄이 모두 체크됐는지로 계산한다.
  - 결과로 체크한 약관 ID 목록을 돌려준다.
- 로그인 버튼을 누르면 목록을 받고 나서 시트를 띄운다. 받지 못했거나 목록이 비어 있으면 시트를 띄우지 않고 재시도할 수 있는 안내를 보여준다. 하드코딩 목록으로 대신하지 않는다.
- 동의한 ID 는 `LoginViewModel` 이 메모리에 들고 있다가 로그인 요청에 `agreedTermsIds` 로 싣는다.
- 서버가 `TERMS_AGREEMENT_REQUIRED` 를 주면(시트를 띄운 뒤 약관이 바뀐 경우) 동의 상태를 비우고 "약관이 변경되었어요. 다시 동의해 주세요"를 안내한다. 다음 시도에서 목록을 다시 받는다.
- 알림 선택값을 기기에 보관했다가 닉네임 설정 뒤 `PUT /users/me/notification` 으로 올리던 흐름(`savePendingNotificationOptIn`, `takePendingNotificationOptIn`)을 없앤다. 가입 응답 뒤 FCM 토큰 등록과 OS 알림 권한 요청은 지금 방식대로 둔다.
- 마이페이지와 로그인 화면 하단의 이용약관·개인정보 처리방침 링크는 그대로 둔다.
- 테스트: 시트 위젯 테스트(서버 목록 그대로 렌더링, 필수만 체크 시 버튼 활성화, 전체 동의), 뷰모델 테스트(ID 전달, 목록 실패, `TERMS_AGREEMENT_REQUIRED` 처리).

## 6. 배포 순서와 영향

1. 운영 DB 에 `docs/db/2026-11-27-terms-consent.sql` 실행.
2. 서버 배포.
3. 앱 새 빌드 배포.

서버가 먼저 나가면 **지금 배포돼 있는 앱 빌드(2.0.0+7 이하)로는 신규 가입이 400 으로 막힌다**(동의 ID 를 보내지 않기 때문). 기존 회원 로그인과 자동 로그인은 영향이 없다. 스토어 심사 전이고 기존 회원이 없으므로 받아들인다.

## 7. 구현자에게 맡기는 것

- 엔티티·리포지토리·서비스의 이름과 패키지 배치(기존 `service.auth` 구조를 따른다).
- 앱 쪽 클래스 이름과 DI 등록, 목록을 받는 동안의 로딩 표시 방식(기존 로그인 버튼 로딩 표시를 따른다).
- Swagger 설명 문구.
- `docs/deploy/2026-11-13-release-checklist.md` 에 SQL 실행 항목을 추가할지.

## 8. 이 브랜치에 이미 들어간 것(설계와 무관한 수정)

- OpenAI 요청에 `store: false` (`bf61cdc`)
- 일기 삭제 시 Redis 참조 세트에서도 삭제 (`4056c8c`)
- 방침·약관 문구 수정과 `/ai-consent` 페이지 (`58bcfad`)
