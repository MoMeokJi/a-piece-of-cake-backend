# 약관 목록·동의 기록 서버 관리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 약관 시트의 항목을 서버가 내려주고, 신규 가입 때 필수 동의를 서버가 검증·저장한다.

**Architecture:** 서버에 `terms`, `member_terms_agreement` 테이블과 공개 목록 API(`GET /auth/terms`)를 두고, 소셜 로그인 요청의 `agreedTermsIds` 를 신규 가입 트랜잭션에서 검증·저장한다. 앱은 로그인 버튼을 누를 때 목록을 받아 시트를 그리고, 체크한 ID 를 로그인 요청에 싣는다.

**Tech Stack:** Spring Boot(JPA, MockMvc + H2), Flutter(dio, provider, get_it, flutter_test)

**Spec:** `docs/superpowers/specs/2026-11-27-terms-consent-design.md`

**실행 방식:** 사용자가 "빨리 진행"을 지시해 이 세션에서 바로 구현하고, 끝에 브랜치 전체를 한 번 리뷰한다.

## Global Constraints

- 서버 브랜치 `feat/terms-consent`, 앱 브랜치 `feat/terms-consent`(앱 `dev` 에서 생성). 푸시하지 않는다.
- `git add` 는 파일 경로를 명시한다.
- 필수 약관 동의 기록이 없는 회원이 만들어지면 안 된다.
- 앱에 약관 제목·종류를 하드코딩하지 않는다. 목록을 못 받으면 시트를 띄우지 않는다.
- 에러 코드: `TERMS_AGREEMENT_REQUIRED`(400), `TERMS_NOT_CONFIGURED`(503).
- 초기 약관 4개의 제목·순서·필수 여부·경로는 스펙 4.1 표 그대로.

## Review Focus

1. `agreedTermsIds` 에 `null` 원소, 중복 ID, 모르는 ID 가 섞여 온다 → 500 이 아니라 무시하고 필수 여부만 판단한다. (Task 2 테스트)
2. 같은 소셜 계정의 동시 첫 로그인 → 한쪽만 가입되고 동의 기록 유니크 위반으로 500 이 나지 않는다. (기존 동시성 처리 경로 유지, Task 2)
3. 가입이 400 으로 거절된 뒤 같은 계정으로 다시 시도 → 정상 가입된다(거절이 회원·소셜 계정 행을 남기지 않는다). (Task 2 테스트)
4. 앱: 목록이 빈 배열로 온다 → 시트를 띄우지 않고 실패로 안내한다. (Task 5 테스트)
5. 앱: 시트를 띄운 뒤 약관이 바뀌어 `TERMS_AGREEMENT_REQUIRED` → 동의 상태를 비우고 다음 시도에서 목록을 다시 받는다. (Task 5 테스트)

---

### Task 1: 약관 테이블과 목록 API (서버)

**Files:**
- Create: `entity/Terms.java`, `entity/MemberTermsAgreement.java`, `repository/TermsRepository.java`, `repository/MemberTermsAgreementRepository.java`, `service/TermsService.java`, `controller/TermsController.java`, `dto/response/TermsListResponseDto.java`
- Create: `docs/db/2026-11-27-terms-consent.sql`
- Modify: `config/SecurityConfig.java` (공개 경로 `GET /auth/terms`), `support/IntegrationTestSupport.java` (테스트 전 초기 약관 4개 심기)
- Test: `controller/TermsIntegrationTest.java`

**Interfaces (Produces):**
- `Terms`: `id, code, version, title, required, displayOrder, contentPath, isActive, createdAt`. `Terms.CODE_RECORD_NOTIFICATION = "RECORD_NOTIFICATION"`.
- `TermsRepository.findByIsActiveTrueOrderByDisplayOrderAsc(): List<Terms>`
- `MemberTermsAgreement.of(String memberId, Long termsId, LocalDateTime agreedAt)`
- `MemberTermsAgreementRepository.findByMemberId(String)`, `deleteByMemberId(String)`
- `TermsService.activeTerms(): List<Terms>`
- `IntegrationTestSupport.requiredTermsIds(): List<Long>`, `allTermsIds(): List<Long>`

- [ ] 실패하는 테스트: 토큰 없이 `GET /auth/terms` → 200, `$.terms` 길이 4, 순서·`title`·`required`·`contentPath` 가 스펙 표와 같고 `code` 필드가 없다. 꺼진 행(`is_active=0`)은 빠진다.
- [ ] 구현 후 통과 확인, 커밋.

### Task 2: 가입 시 동의 검증·저장, 탈퇴 시 삭제 (서버)

**Files:**
- Modify: `dto/request/GoogleLoginRequestDto.java`, `AppleLoginRequestDto.java` (`List<Long> agreedTermsIds`, 선택), `service/auth/SocialLoginService.java`, `service/auth/SocialAccountRegistrar.java`, `entity/Member.java` (`create(memberId, notificationEnabled)`), `service/TermsService.java`, `service/AuthService.java`, `common/error/ErrorCode.java`, `controller/UserController.java`(설명 문구), `support/IntegrationTestSupport.java`(`loginAsMember` 가 필수 ID 전송)
- Test: `controller/TermsAgreementIntegrationTest.java`, 기존 `AuthIntegrationTest`·`WithdrawIntegrationTest` 등 로그인 헬퍼 수정

**Interfaces:**
- `TermsService.resolveForSignup(List<Long> agreedTermsIds): SignupTerms` — 활성 필수 약관이 없으면 `TERMS_NOT_CONFIGURED`, 필수 누락이면 `TERMS_AGREEMENT_REQUIRED`. `record SignupTerms(List<Long> agreedTermsIds, boolean notificationOptIn)` 은 활성 약관 ID 만 담는다.
- `SocialAccountRegistrar.register(SocialIdentity, SignupTerms): SocialAccount` — 회원·소셜 계정·동의 기록을 한 트랜잭션으로.
- 검증은 소셜 계정 조회가 비었을 때만, `register` 전에 한다.

- [ ] 실패하는 테스트:
  - 필드 없음 / 빈 배열 / 필수 하나 누락 → 400 `TERMS_AGREEMENT_REQUIRED`, `member`·`social_account` 행 0개. 이어서 전부 동의로 재시도하면 200.
  - 전부 동의(선택 포함) → 200, 동의 기록 4행, `GET /users/me` 의 `notificationEnabled` true.
  - 필수만 동의 → 동의 기록 3행, `notificationEnabled` false.
  - `[null, 999999, 필수들…, 필수 중복]` → 200, 동의 기록 3행.
  - 활성 필수 약관이 없을 때 → 503 `TERMS_NOT_CONFIGURED`.
  - 기존 회원이 `agreedTermsIds` 없이 로그인 → 200, 동의 기록 수 변화 없음. 알림 항목을 넣어 로그인해도 `notificationEnabled` 그대로.
  - 애플 로그인도 같은 검증을 탄다.
  - 탈퇴 후 `member_terms_agreement` 에 그 회원 행 0개.
- [ ] 구현 후 전체 테스트(`./gradlew test`) 통과 확인, 커밋.

### Task 3: 앱 — 약관 목록 받기

**Files (앱 저장소):**
- Create: `lib/domain/model/terms_item.dart`, `lib/domain/repository/terms_repository.dart`, `lib/data/data_source/api/terms/terms_api.dart`, `terms_api_impl.dart`, `lib/data/repository/terms_repository_impl.dart`, `test/fakes/fake_terms_repository.dart`
- Modify: `lib/config/di.dart`
- Test: `test/data/data_source/api/terms/terms_api_impl_test.dart`

**Interfaces (Produces):**
- `TermsItem({required int id, required String title, required bool required, String? contentPath})`
- `TermsRepository.fetchTerms(): Future<List<TermsItem>>` — 네트워크 단절은 `NetworkUnavailableException`.

- [ ] 실패하는 테스트: `GET /auth/terms` 를 인증 헤더 없이 부르고 응답을 순서대로 파싱한다(`contentPath` null 포함).
- [ ] 구현, 통과, 커밋.

### Task 4: 앱 — 약관 시트를 목록으로 그리기

**Files:**
- Modify: `lib/presentation/login/components/terms_sheet.dart`, `lib/config/api_config.dart`(경로를 기본 주소에 붙이는 `pageUrl(String path)`)
- Test: `test/presentation/login/terms_sheet_test.dart`

**Interfaces (Produces):**
- `TermsSheet.show(BuildContext, List<TermsItem>): Future<List<int>?>` — 체크한 약관 ID. 닫으면 null.

- [ ] 실패하는 위젯 테스트: 받은 제목이 순서대로 `[필수]`/`[선택]` 과 함께 보인다. 필수만 체크하면 버튼이 켜지고 결과에 필수 ID 만 있다. "전체 동의하기"는 모든 줄을 체크/해제한다. `contentPath` 가 없는 줄에는 보기 버튼이 없다.
- [ ] 구현, 통과, 커밋.

### Task 5: 앱 — 로그인에 동의 ID 전달, 알림 보관 흐름 제거

**Files:**
- Modify: `login_screen.dart`, `login_view_model.dart`, `auth_repository.dart`, `auth_repository_impl.dart`, `auth_api.dart`, `auth_api_impl.dart`, `api_exception.dart`(`termsAgreementRequired`), `api_error_mapper.dart`, `app_exception.dart`(`TermsAgreementRequiredException`), `app_error_messages.dart`, `nickname_view_model.dart`, `app_prefs_repository.dart`, `app_prefs_repository_impl.dart`, `di.dart`, 관련 fake·테스트
- Test: `login_view_model_test.dart`, `nickname_view_model_test.dart`, `auth_api_impl_test.dart`

**Interfaces:**
- `LoginViewModel.loadTerms(): Future<List<TermsItem>?>` — 실패·빈 목록이면 null 을 주고 재시도 가능한 토스트를 건다.
- `LoginViewModel.agreeTerms(List<int> agreedTermsIds)`, `needsTermsAgreement`
- `AuthRepository.signIn(SocialProvider, {required List<int> agreedTermsIds})`

- [ ] 실패하는 테스트: 로그인 요청 본문에 `agreedTermsIds`. 목록 실패/빈 목록 → null + 토스트. `TermsAgreementRequiredException` → 동의 상태 초기화 + "약관이 변경되었어요. 다시 동의해 주세요". 닉네임 온보딩이 더 이상 `updateNotification` 을 부르지 않고 FCM 등록은 한다.
- [ ] 구현, `flutter analyze` 와 `flutter test` 통과, 커밋.

### Task 6: 가입 화면 노출 확인과 마무리

- [ ] 로컬 서버(테스트 프로필과 같은 초기 약관)와 앱을 붙여 시뮬레이터에서 시트에 4개 항목이 서버 순서대로 뜨는지 확인한다. 로컬 서버를 띄울 수 없으면 위젯 테스트 결과와 그 한계를 보고한다.
- [ ] `docs/deploy/2026-11-13-release-checklist.md` 에 SQL 실행·배포 순서를 추가한다.
- [ ] 브랜치 전체 리뷰 1회, 지적 사항 전부 반영.
