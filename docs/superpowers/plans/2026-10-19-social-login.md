# 소셜 로그인 + 인증 구조 개편 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 모바일 앱에 Google/Apple 소셜 가입·로그인(자동 로그인 포함)을 추가하고, 기존 JWT 구조를 서버 저장 refresh 회전 + 체인 분리 + Problem Details 에러 체계로 교체한다.

**Architecture:** access 토큰은 Spring Security OAuth2 Resource Server(Nimbus, HS256)로 검증한다. 앱 체인과 관리자 체인(`/admin/**`)을 나누고 서로 다른 키를 쓴다. 앱 refresh 토큰은 opaque 랜덤 값이며 `member_device` 행(기기 1대 = 세션 1개)에 SHA-256 해시로 저장해 회전한다. 소셜 ID 토큰은 공급자 JWKS로 검증한다. 관리자는 access만 쓰고 `admin.token_version`으로 무효화한다.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Security 6 (oauth2-resource-server, Nimbus JOSE), Spring Data JPA (MySQL 운영 / H2 테스트), Firebase Admin, JUnit 5 + MockMvc + Mockito.

**Spec:** `docs/superpowers/specs/2026-10-19-social-login-design.md` — 각 태스크는 이 스펙의 절 번호를 근거로 한다. 실행자는 스펙과 이 계획을 함께 읽는다.

## Global Constraints

- 베이스 패키지: `com.momeokji.aiDiarybackend`. 기존 레이어 패키지(`config`, `controller`, `service`, `repository`, `entity`, `dto`, `security`, `common`)를 따른다. 기존 코드처럼 탭 들여쓰기, Lombok, 한국어 주석/메시지.
- 앱 access 30분(`30m`), 앱 refresh 60일(`60d`), 관리자 access 2시간(`2h`), refresh 재시도 유예 60초.
- 앱 JWT: `aud=app`, `role=USER`, `sub=member_id`. 관리자 JWT: `aud=admin`, `role=ADMIN|SUPER_ADMIN`, `ver=token_version`. 둘 다 `iss=jwt.issuer`, HS256, 서로 다른 키(각 32바이트 이상).
- 외부 노출 식별자는 UUID만(`member_id`, `device_id`). `social_account.id`는 응답/토큰/URL에 넣지 않는다.
- 토큰 원문(소셜/access/refresh/애플 refresh)은 로그에 남기지 않는다.
- 에러 응답은 RFC 9457 `ProblemDetail` + 확장 필드 `code`(스펙 9.2의 코드 문자열 그대로).
- `ddl-auto=validate`(운영)와 호환되도록 엔티티 매핑과 `docs/db/*.sql`의 타입을 맞춘다. 문자열 해시 컬럼은 `VARCHAR(64)`(Hibernate validate가 CHAR와 VARCHAR를 다르게 보므로 CHAR를 쓰지 않는다).
- git push 금지. 커밋만 한다. 커밋 메시지는 기존 컨벤션(`feat. ...`, `refactor. ...`, `test. ...`, `docs. ...`, `build. ...`)을 따르고 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`를 붙인다.
- 테스트 명령: `./gradlew test --tests '<FQCN 또는 패턴>'`. 전체: `./gradlew test`.

## Review Focus

1. 같은 기기에서 **fcmToken 없이** 다른 회원이 로그인 → 이전 회원의 `fcm_token`이 남으면 안 된다(NULL). 같은 회원 재로그인에 fcmToken이 없으면 기존 값을 유지한다. → Task 3 테스트 `reassignToOtherMemberWithoutFcmTokenClearsIt`, `sameMemberReloginWithoutFcmTokenKeepsIt`
2. 다른 기기의 refresh 토큰을 내 `deviceId`로 보냄 → `REFRESH_INVALID`이고 **그 다른 기기 세션은 그대로** 살아 있어야 한다. → Task 5 `refreshOfAnotherDeviceIsRejectedWithoutTouchingIt`
3. 애플 재로그인 시 `email` claim이 없음 → 저장된 email을 NULL로 덮어쓰지 않는다. → Task 10 `appleReloginWithoutEmailKeepsStoredEmail`
4. `deviceId`가 UUID 형식이 아니거나 `os`가 `AND|IOS`가 아님 → 400 `VALIDATION_FAILED`(500 아님). → Task 10 `invalidDeviceIdOrOsIsValidationFailed`
5. 같은 소셜 계정의 첫 로그인이 동시에 두 번 → 유니크 위반을 재조회로 흡수하고 같은 `member_id`로 로그인된다. → Task 10 `concurrentFirstLoginFallsBackToExistingAccount`

---

## File Map

| 파일 | 책임 | 태스크 |
|---|---|---|
| `build.gradle` | H2, security-test, oauth2-resource-server 추가 / jjwt 제거 | 1, 4, 10 |
| `src/test/resources/application-test.properties` | 테스트 프로필 | 1 (이후 보강) |
| `src/test/java/.../support/IntegrationTestSupport.java` | 통합 테스트 베이스 | 1 (이후 보강) |
| `config/FirebaseConfig.java` | `firebase.enabled`로 끌 수 있게 | 1 |
| `common/error/{ErrorCode,BusinessException,ProblemDetailFactory,GlobalExceptionHandler}.java` | 에러 체계 | 2 |
| `entity/{SocialProvider,SocialAccount,MemberDevice}.java`, `entity/Admin.java` | 데이터 모델 | 3 |
| `repository/{SocialAccountRepository,MemberDeviceRepository}.java` | 조회/갱신 | 3 |
| `docs/db/2026-10-19-social-login-schema.sql` | 운영 DDL | 3 |
| `config/{JwtProperties,JwtConfig,ClockConfig}.java`, `security/{AccessTokenIssuer,IssuedAccessToken,RefreshTokens}.java` | 토큰 발급/검증 | 4 |
| `service/auth/{DeviceSessionService,IssuedTokens}.java` | 기기 세션, refresh 회전 | 5 |
| `config/{OAuthProperties,SocialTokenDecoderConfig}.java`, `service/auth/social/*` | 소셜 토큰 검증 | 6 |
| `service/auth/apple/*` | 애플 code 교환, revoke, 암호화 | 7 |
| `config/SecurityConfig.java`, `security/*Converter.java`, `security/{PublicPathBearerTokenResolver,ProblemDetailsAuthenticationEntryPoint,ProblemDetailsAccessDeniedHandler}.java`, 관리자 컨트롤러/서비스 | 체인 분리, 관리자 이전 | 8 |
| `service/push/{FcmSender,FirebaseFcmSender}.java`, `service/FcmService.java`, 스케줄러 | 기기별 푸시 | 9 |
| `service/auth/{SocialLoginService,SocialLoginResult}.java`, `controller/AuthController.java`, `dto/request/*`, `dto/response/AuthTokenResponseDto.java` | 앱 인증 API + 구 코드 삭제 | 10 |
| `service/AuthService.java` | 탈퇴 확장 | 11 |
| `docs/db/2026-10-19-cleanup-anonymous-members.sql`, `config/OpenApiConfig.java`, `application.properties` | 마무리 | 12 |

---

### Task 1: 테스트 기반 만들기

현재 테스트는 `contextLoads()` 하나이고 실제 DB/Redis/Firebase가 있어야 뜬다. 로컬에 Docker가 없으므로 H2(MySQL 모드)로 컨텍스트가 뜨게 만든다. (스펙 11.1)

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/java/com/momeokji/aiDiarybackend/config/FirebaseConfig.java`
- Create: `src/test/resources/application-test.properties`
- Create: `src/test/java/com/momeokji/aiDiarybackend/support/IntegrationTestSupport.java`
- Modify: `src/test/java/com/momeokji/aiDiarybackend/AiDiarybackendApplicationTests.java`

**Interfaces:**
- Produces: `abstract class IntegrationTestSupport` — `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`, `protected MockMvc mockMvc`, `protected ObjectMapper objectMapper`, `@MockitoBean protected RedisService redisService`, `@AfterEach void cleanDatabase()`(JdbcTemplate로 `SET REFERENTIAL_INTEGRITY FALSE` → `INFORMATION_SCHEMA.TABLES`의 `PUBLIC` 스키마 테이블 전부 `TRUNCATE` → `TRUE`). 클래스에 `@Transactional`을 걸지 않는다 — Task 10의 `REQUIRES_NEW` 생성 로직이 테스트 트랜잭션 밖에서 커밋되기 때문이다. 이후 태스크가 `@MockitoBean`/`@Import`를 이 클래스에 추가한다.

- [ ] **Step 1: 의존성 추가** — `build.gradle`에 `testRuntimeOnly 'com.h2database:h2'`, `testImplementation 'org.springframework.security:spring-security-test'`.

- [ ] **Step 2: `FirebaseConfig`에 `@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)`** — 운영 동작은 그대로, 테스트에서만 끈다.

- [ ] **Step 3: `application-test.properties` 작성** — 아래 키를 모두 넣는다(값은 더미). `application.properties`의 `${...}` 자리표시자가 환경변수 없이도 풀리게 하는 것이 목적이다.
  - `spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`, `spring.datasource.username=sa`, `spring.datasource.password=`
  - `spring.jpa.hibernate.ddl-auto=create-drop`, `spring.jpa.show-sql=false`
  - `spring.main.allow-bean-definition-overriding=true`
  - `firebase.enabled=false`
  - `openai.secret-key=test`, `openai.prompts.{content,summary,colors,music,sympathy}.id=test`
  - `aws.s3.access-key=test`, `aws.s3.secret-access-key=test`
  - `spring.data.redis.host=localhost`, `spring.data.redis.port=6379`
  - 기존 jwt 키(`jwt.secret`, `jwt.issuer`, `jwt.expiration.access-token-ms`, `jwt.expiration.refresh-token-ms`) — 32바이트 이상 더미 secret
  - `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`

- [ ] **Step 4: `IntegrationTestSupport` 작성** (Interfaces 참고), `AiDiarybackendApplicationTests`가 이를 상속하도록 변경.

- [ ] **Step 5: 실행** — `./gradlew test --tests 'com.momeokji.aiDiarybackend.AiDiarybackendApplicationTests'` → PASS. 로컬 `.env`가 없어도 통과해야 한다(`.env`를 잠시 다른 이름으로 바꿔 확인 후 원복).

- [ ] **Step 6: Commit** — `test. H2 기반 테스트 프로필과 통합 테스트 베이스 추가`

---

### Task 2: Problem Details 에러 체계

(스펙 9장) 전역 예외 처리를 만든다. 기존 `IllegalArgumentException`은 400 `BAD_REQUEST`가 된다.

**Files:**
- Create: `src/main/java/com/momeokji/aiDiarybackend/common/error/ErrorCode.java`
- Create: `src/main/java/com/momeokji/aiDiarybackend/common/error/BusinessException.java`
- Create: `src/main/java/com/momeokji/aiDiarybackend/common/error/ProblemDetailFactory.java`
- Create: `src/main/java/com/momeokji/aiDiarybackend/common/error/GlobalExceptionHandler.java`
- Test: `src/test/java/com/momeokji/aiDiarybackend/common/error/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces:
  - `enum ErrorCode { UNAUTHORIZED, TOKEN_EXPIRED, TOKEN_INVALID, REFRESH_INVALID, REFRESH_REUSED, SOCIAL_TOKEN_INVALID, ADMIN_LOGIN_FAILED, FORBIDDEN, ADMIN_NOT_APPROVED, VALIDATION_FAILED, BAD_REQUEST, DEVICE_NOT_FOUND, SOCIAL_PROVIDER_UNAVAILABLE, INTERNAL_ERROR }` — 각 상수는 `HttpStatus status()`와 `String defaultDetail()`을 가진다. 상태는 스펙 9.2 표 그대로.
  - `class BusinessException extends RuntimeException` — `BusinessException(ErrorCode)`, `BusinessException(ErrorCode, String detail)`, `ErrorCode getErrorCode()`.
  - `final class ProblemDetailFactory` — `static ProblemDetail create(ErrorCode code, String detail, String instancePath)`: `status`, `title`(=상태 reason phrase), `detail`(null이면 `defaultDetail`), `instance`, 확장 속성 `code`(=`code.name()`).
  - `@RestControllerAdvice class GlobalExceptionHandler extends ResponseEntityExceptionHandler`

- [ ] **Step 1: 실패하는 테스트 작성** — `MockMvcBuilders.standaloneSetup(new ThrowingController()).setControllerAdvice(new GlobalExceptionHandler())`로 구성. `ThrowingController`는 테스트 안의 정적 중첩 클래스이며 경로마다 아래 예외를 던진다.

```java
@Test void businessExceptionBecomesProblemDetailWithCode() throws Exception {
    mockMvc.perform(get("/throw/business"))   // throws new BusinessException(ErrorCode.DEVICE_NOT_FOUND)
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.instance").value("/throw/business"));
}
@Test void illegalArgumentBecomesBadRequestKeepingMessage()   // "text는 필수입니다." → 400, code BAD_REQUEST, detail 그대로
@Test void beanValidationFailureIsValidationFailed()          // @Valid body 누락 필드 → 400, code VALIDATION_FAILED
@Test void unreadableBodyIsValidationFailed()                 // 깨진 JSON → 400, code VALIDATION_FAILED
@Test void unexpectedExceptionHidesInternals()                // RuntimeException("db password=xxx") → 500, code INTERNAL_ERROR, detail에 "password" 미포함
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*GlobalExceptionHandlerTest'` → 컴파일 실패(클래스 없음).

- [ ] **Step 3: 구현**
  - `ErrorCode`, `BusinessException`, `ProblemDetailFactory`를 Interfaces대로 만든다.
  - `GlobalExceptionHandler`:
    - `@ExceptionHandler(BusinessException.class)` → `ProblemDetailFactory.create(e.getErrorCode(), e.getMessage(), request URI)`.
    - `@ExceptionHandler(IllegalArgumentException.class)` → `BAD_REQUEST`, detail = 예외 메시지.
    - `@ExceptionHandler(Exception.class)` → `INTERNAL_ERROR`, detail = 기본 문구. `log.error`로 스택 기록.
    - `handleExceptionInternal` override: Spring MVC 표준 예외(검증, 본문 파싱, 파라미터 누락 등)의 `ProblemDetail` 바디에 `code`를 붙인다. 상태가 400이면 `VALIDATION_FAILED`, 그 외에는 `HttpStatus` 이름(예: `NOT_FOUND`).
  - 응답 Content-Type은 `application/problem+json`.

- [ ] **Step 4: 실행해서 통과 확인** — 같은 명령 → PASS. 이어서 `./gradlew test` 전체 → PASS.

- [ ] **Step 5: Commit** — `feat. RFC 9457 Problem Details 기반 전역 예외 처리 추가`

---

### Task 3: 신규 테이블 엔티티와 DDL

(스펙 3.2~3.4, 3.6)

**Files:**
- Create: `entity/SocialProvider.java`, `entity/SocialAccount.java`, `entity/MemberDevice.java`
- Modify: `entity/Admin.java`
- Create: `repository/SocialAccountRepository.java`, `repository/MemberDeviceRepository.java`
- Create: `docs/db/2026-10-19-social-login-schema.sql`
- Test: `src/test/java/com/momeokji/aiDiarybackend/repository/MemberDeviceRepositoryTest.java`, `.../repository/SocialAccountRepositoryTest.java`

**Interfaces:**
- Produces:
  - `enum SocialProvider { GOOGLE, APPLE }`
  - `SocialAccount` (`@Table(name="social_account")`, `@Enumerated(STRING) provider`, 유니크 `(provider, provider_user_id)`):
    `static SocialAccount link(String memberId, SocialProvider provider, String providerUserId, String email)`, `void updateEmailIfPresent(String email)`(null/blank면 무시), `void storeAppleRefreshToken(String encrypted)`, getters `getMemberId()`, `getProvider()`, `getProviderUserId()`, `getEmail()`, `getAppleRefreshToken()`.
  - `MemberDevice` (`@Table(name="member_device")`, PK `deviceId` String(36), 컬럼은 스펙 3.3, 해시는 `length = 64`):
    - `static MemberDevice open(String deviceId, String memberId, String os, String fcmToken, String refreshTokenHash, LocalDateTime refreshExpiresAt, LocalDateTime now)`
    - `void reassign(String memberId, String os, String fcmToken, String refreshTokenHash, LocalDateTime refreshExpiresAt, LocalDateTime now)` — 같은 회원이고 `fcmToken == null`이면 기존 fcm 유지, 다른 회원이면 전달값(NULL 포함)으로 교체. `prevRefreshTokenHash`, `rotatedAt`은 항상 NULL. `lastUsedAt = now`.
    - `void rotate(String newRefreshTokenHash, LocalDateTime refreshExpiresAt, LocalDateTime now)` — prev ← 현재, 현재 ← new, `rotatedAt = lastUsedAt = now`.
    - `void changeFcmToken(String fcmToken)`, `boolean isRefreshExpired(LocalDateTime now)`, `boolean isWithinRetryGrace(LocalDateTime now, Duration grace)`
  - `SocialAccountRepository extends JpaRepository<SocialAccount, Long>`: `Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider, String)`, `List<SocialAccount> findByMemberId(String)`, `void deleteByMemberId(String)`
  - `MemberDeviceRepository extends JpaRepository<MemberDevice, String>`:
    - `@Lock(PESSIMISTIC_WRITE) Optional<MemberDevice> findWithLockByDeviceId(String deviceId)`
    - `Optional<MemberDevice> findByDeviceIdAndMemberId(String deviceId, String memberId)`
    - `List<MemberDevice> findByMemberIdAndFcmTokenIsNotNull(String memberId)`, `List<MemberDevice> findByFcmTokenIsNotNull()`
    - `@Modifying @Query int detachFcmTokenFromOthers(@Param("fcmToken") String fcmToken, @Param("deviceId") String deviceId)` — `deviceId`가 아닌 행에서 같은 토큰을 NULL로
    - `@Modifying @Query int clearFcmToken(@Param("fcmToken") String fcmToken)`
    - `void deleteByMemberId(String memberId)`
    - `@Query boolean existsPushableDevice(@Param("memberId") String memberId)` — `fcm_token IS NOT NULL`인 행 존재 여부
  - `Admin`: `Integer tokenVersion` (`@Column(name="token_version", nullable=false)`, `@ColumnDefault("0")`, 기존 `@DynamicInsert`로 기본값 사용), `void bumpTokenVersion()`; 기존 `delete(LocalDateTime)`도 내부에서 `bumpTokenVersion()` 호출.

- [ ] **Step 1: 실패하는 테스트 작성** — `@DataJpaTest @ActiveProfiles("test")`.

```java
// MemberDeviceRepositoryTest
@Test void fcmTokenIsUnique()                       // 두 행에 같은 fcm 저장 후 flush → DataIntegrityViolationException
@Test void detachFcmTokenFromOthersClearsOnlyOtherRows() // A(fcm=X), B(fcm=null) → detach("X","B") → A.fcm == null, 반환값 1
@Test void reassignToOtherMemberWithoutFcmTokenClearsIt()   // open(m1, fcm=X) → reassign(m2, fcm=null) → fcm == null, prev/rotatedAt == null
@Test void sameMemberReloginWithoutFcmTokenKeepsIt()        // open(m1, fcm=X) → reassign(m1, fcm=null) → fcm == "X"
@Test void rotateMovesCurrentHashToPrev()                   // rotate("h2") → current "h2", prev "h1", rotatedAt == now
// SocialAccountRepositoryTest
@Test void providerAndProviderUserIdIsUnique()      // 같은 (GOOGLE, "sub-1") 두 번 → DataIntegrityViolationException
@Test void updateEmailIfPresentIgnoresNull()        // email "a@x.com" → updateEmailIfPresent(null) → 여전히 "a@x.com"
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*RepositoryTest'` → 컴파일 실패.

- [ ] **Step 3: 엔티티, 리포지토리 구현** (Interfaces 그대로).

- [ ] **Step 4: DDL 작성** — `docs/db/2026-10-19-social-login-schema.sql`: `CREATE TABLE social_account`, `CREATE TABLE member_device`(스펙 3.2/3.3의 컬럼, 인덱스, 유니크; 해시 컬럼 `VARCHAR(64)`; `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`), `ALTER TABLE admin ADD COLUMN token_version INT NOT NULL DEFAULT 0;`. 파일 머리 주석에 "새 서버 배포 전에 실행"을 적는다.

- [ ] **Step 5: 실행해서 통과 확인** — 같은 명령 → PASS. `./gradlew test` → PASS.

- [ ] **Step 6: Commit** — `feat. social_account, member_device 엔티티와 admin.token_version 추가`

---

### Task 4: 토큰 발급과 검증 기반

(스펙 4.1, 4.2, 4.4, 10장) Nimbus 기반 인코더/디코더를 만든다. 아직 Security 체인에는 연결하지 않는다.

**Files:**
- Modify: `build.gradle` — `implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'`
- Create: `config/JwtProperties.java`, `config/JwtConfig.java`, `config/ClockConfig.java`
- Create: `security/AccessTokenIssuer.java`, `security/IssuedAccessToken.java`, `security/RefreshTokens.java`
- Modify: `src/main/resources/application.properties` — 스펙 10장의 `jwt.member.*`, `jwt.admin.*` 키 추가(기존 `jwt.secret`, `jwt.expiration.*`은 Task 10에서 제거하므로 지금은 유지)
- Modify: `src/test/resources/application-test.properties` — 새 키 더미값
- Test: `src/test/java/com/momeokji/aiDiarybackend/security/AccessTokenIssuerTest.java`, `.../security/RefreshTokensTest.java`

**Interfaces:**
- Produces:
  - `@ConfigurationProperties("jwt") record JwtProperties(String issuer, MemberToken member, AdminToken admin)` — `record MemberToken(String secret, Duration accessTokenTtl, Duration refreshTokenTtl)`, `record AdminToken(String secret, Duration accessTokenTtl)`. `config` 패키지에 둬서 기존 `@ConfigurationPropertiesScan`에 잡히게 한다.
  - `JwtConfig`: `static SecretKey hmacKey(String secret)`(UTF-8 바이트가 32 미만이면 `IllegalStateException`), 빈 `JwtDecoder memberJwtDecoder(JwtProperties)`, `JwtDecoder adminJwtDecoder(JwtProperties)` — HS256, validator = 기본(만료, 60초 오차) + `iss` 일치 + `aud`에 각각 `"app"` / `"admin"` 포함.
  - `ClockConfig`: `@Bean Clock clock()` = `Clock.systemDefaultZone()`.
  - `record IssuedAccessToken(String value, long expiresInSeconds)`
  - `@Component AccessTokenIssuer(JwtProperties, Clock)`: `static final String MEMBER_AUDIENCE = "app"`, `ADMIN_AUDIENCE = "admin"`; `IssuedAccessToken issueForMember(String memberId)`; `IssuedAccessToken issueForAdmin(Admin admin)` (role = `isSuper ? "SUPER_ADMIN" : "ADMIN"`, `ver` = `tokenVersion`). `NimbusJwtEncoder`(`ImmutableSecret`) 두 개를 사용.
  - `final class RefreshTokens`: `static String generate()`(32바이트 `SecureRandom`, base64url, 패딩 없음), `static String sha256Hex(String value)`

- [ ] **Step 1: 실패하는 테스트 작성** — Spring 없이 직접 생성(`new AccessTokenIssuer(props, fixedClock)`, `new JwtConfig().memberJwtDecoder(props)`).

```java
@Test void memberTokenHasSpecClaims() {
    Jwt jwt = memberDecoder.decode(issuer.issueForMember("m-1").value());
    assertThat(jwt.getSubject()).isEqualTo("m-1");
    assertThat(jwt.getAudience()).containsExactly("app");
    assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
    assertThat(jwt.getClaims()).doesNotContainKey("did");
}
@Test void memberTokenExpiresIn30Minutes()            // expiresInSeconds == 1800, exp - iat == 1800s
@Test void memberTokenIsRejectedByAdminDecoder()      // adminDecoder.decode(memberToken) → JwtException
@Test void adminTokenCarriesRoleAndVersion()          // isSuper=true, tokenVersion=3 → role "SUPER_ADMIN", ver 3, aud "admin", expiresIn 7200
@Test void expiredTokenIsRejected()                   // clock 31분 전 발급 → JwtValidationException, 메시지에 "Jwt expired at"
@Test void shortSecretFailsFast()                     // hmacKey("short") → IllegalStateException
// RefreshTokensTest
@Test void generateIsUrlSafeAndUnique()               // 43자, [A-Za-z0-9_-]만, 두 번 호출 결과 다름
@Test void sha256HexIsDeterministic64Chars()
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*AccessTokenIssuerTest' --tests '*RefreshTokensTest'` → 컴파일 실패.

- [ ] **Step 3: 구현** (Interfaces 그대로). `application.properties`에는 스펙 10장 키를 그대로 추가하고 TTL 기본값(`30m`, `60d`, `2h`)을 넣는다.

- [ ] **Step 4: 실행해서 통과 확인** → PASS. `./gradlew test` → PASS(기존 `JwtAuthFilter`는 아직 동작 중이어야 한다).

- [ ] **Step 5: Commit** — `feat. Nimbus 기반 access 토큰 발급기와 앱/관리자 디코더 추가`

---

### Task 5: 기기 세션과 refresh 회전

(스펙 3.5, 4.2, 4.3)

**Files:**
- Create: `service/auth/IssuedTokens.java`, `service/auth/DeviceSessionService.java`
- Test: `src/test/java/com/momeokji/aiDiarybackend/service/auth/DeviceSessionServiceTest.java`

**Interfaces:**
- Consumes: `MemberDeviceRepository`(Task 3), `AccessTokenIssuer`, `RefreshTokens`, `JwtProperties`, `Clock`(Task 4), `BusinessException`/`ErrorCode`(Task 2)
- Produces:
  - `record IssuedTokens(String accessToken, long accessTokenExpiresIn, String refreshToken, long refreshTokenExpiresIn)`
  - `@Service DeviceSessionService`:
    - `static final Duration REFRESH_RETRY_GRACE = Duration.ofSeconds(60)`
    - `@Transactional IssuedTokens openSession(String memberId, String deviceId, String os, String fcmToken)` — 행이 없으면 `open`, 있으면 `reassign`. `fcmToken != null`이면 먼저 `detachFcmTokenFromOthers(fcmToken, deviceId)`.
    - `@Transactional(noRollbackFor = BusinessException.class) IssuedTokens rotate(String refreshToken, String deviceId)` — 스펙 4.3 알고리즘. `findWithLockByDeviceId`로 잠근 뒤 현재/직전 해시 비교. 만료·재사용으로 행을 지운 뒤 예외를 던지므로 `noRollbackFor`가 필수다.
    - `@Transactional void logout(String memberId, String deviceId)` — `findByDeviceIdAndMemberId` 있으면 삭제, 없으면 아무것도 안 함.
    - `@Transactional void updateFcmToken(String memberId, String deviceId, String fcmToken)` — 내 기기가 아니면 `DEVICE_NOT_FOUND`. detach 후 변경.
    - `@Transactional void deleteAllOf(String memberId)`

- [ ] **Step 1: 실패하는 테스트 작성** — `@DataJpaTest @ActiveProfiles("test")`. 서비스는 직접 생성하고, `Clock`은 테스트 안의 가변 clock(`MutableClock` 중첩 클래스 — `Instant`를 앞당기는 `advance(Duration)` 제공)을 쓴다.

```java
@Test void openSessionIssuesTokensAndStoresOnlyHash() {
    IssuedTokens t = service.openSession("m1", DEVICE_A, "IOS", "fcm-1");
    MemberDevice d = repo.findById(DEVICE_A).orElseThrow();
    assertThat(d.getRefreshTokenHash()).isEqualTo(RefreshTokens.sha256Hex(t.refreshToken()));
    assertThat(d.getRefreshTokenHash()).isNotEqualTo(t.refreshToken());
    assertThat(t.accessTokenExpiresIn()).isEqualTo(1800);
    assertThat(t.refreshTokenExpiresIn()).isEqualTo(5_184_000);
}
@Test void rotateInvalidatesPreviousTokenAfterGrace()   // rotate(t1) → t2; clock +61s; rotate(t1) → REFRESH_REUSED, 행 삭제됨, rotate(t2)도 REFRESH_INVALID
@Test void retryWithPreviousTokenWithinGraceIssuesNewPair() // rotate(t1) → t2; clock +10s; rotate(t1) → 성공(t3), rotate(t3) 성공
@Test void expiredRefreshDeletesSessionAndIsInvalid()   // clock +60d+1s → REFRESH_INVALID, 행 삭제됨
@Test void unknownTokenIsInvalid()                      // rotate("garbage", DEVICE_A) → REFRESH_INVALID, 기존 행 유지
@Test void refreshOfAnotherDeviceIsRejectedWithoutTouchingIt() // A,B 세션; rotate(tokenOfB, DEVICE_A) → REFRESH_INVALID; rotate(tokenOfB, DEVICE_B) 성공
@Test void otherMemberLoginOnSameDeviceKillsPreviousRefresh() // m1 openSession → t1; m2 openSession(같은 기기, fcm=null) → rotate(t1) REFRESH_INVALID, fcm == null
@Test void fcmTokenMovesToNewDevice()                   // A(fcm=X); openSession(B, fcm=X) → A.fcm == null, B.fcm == X
@Test void logoutIsIdempotent()                         // logout 두 번, 존재하지 않는 기기 → 예외 없음
@Test void updateFcmTokenOnForeignDeviceIsNotFound()    // m2가 m1 기기 갱신 → DEVICE_NOT_FOUND
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*DeviceSessionServiceTest'` → 컴파일 실패.

- [ ] **Step 3: 구현** (Interfaces 그대로).

- [ ] **Step 4: 실행해서 통과 확인** → PASS.

- [ ] **Step 5: Commit** — `feat. 기기 세션 기반 refresh 토큰 회전과 재사용 감지 구현`

---

### Task 6: 소셜 ID 토큰 검증

(스펙 5.1~5.3 검증 부분)

**Files:**
- Create: `config/OAuthProperties.java`, `config/SocialTokenDecoderConfig.java`
- Create: `service/auth/social/SocialIdentity.java`, `service/auth/social/SocialTokenErrors.java`, `service/auth/social/GoogleIdTokenVerifier.java`, `service/auth/social/AppleIdTokenVerifier.java`
- Modify: `application.properties`, `application-test.properties` — `oauth.*` 키(스펙 10장). 로컬 실행을 위해 `${GOOGLE_CLIENT_IDS:}`처럼 빈 기본값을 두되, `oauth.apple.token-encryption-key=${APPLE_TOKEN_ENCRYPTION_KEY}`만은 기본값 없이 둔다(Task 7에서 빠른 실패)
- Test: `src/test/java/com/momeokji/aiDiarybackend/service/auth/social/{GoogleIdTokenVerifierTest,AppleIdTokenVerifierTest}.java`, `src/test/java/com/momeokji/aiDiarybackend/support/TestSocialTokens.java`

**Interfaces:**
- Consumes: `RefreshTokens.sha256Hex`(Task 4), `SocialProvider`(Task 3), `BusinessException`(Task 2)
- Produces:
  - `@ConfigurationProperties("oauth") record OAuthProperties(Google google, Apple apple)` — `record Google(List<String> clientIds)`, `record Apple(String clientId, String teamId, String keyId, String privateKeyPath, String tokenEncryptionKey)`
  - `SocialTokenDecoderConfig`: 빈 `JwtDecoder googleIdTokenDecoder(OAuthProperties)`(JWKS `https://www.googleapis.com/oauth2/v3/certs`, RS256), `JwtDecoder appleIdTokenDecoder(OAuthProperties)`(JWKS `https://appleid.apple.com/auth/keys`, RS256). 정적 `OAuth2TokenValidator<Jwt> googleValidator(List<String> clientIds)`(만료 + `iss` ∈ {`https://accounts.google.com`, `accounts.google.com`}, `String.valueOf`로 비교 + `aud` 교집합), `appleValidator(String clientId)`(만료 + `iss` = `https://appleid.apple.com` + `aud` 포함). 테스트 디코더도 이 정적 메서드를 써서 검증 규칙이 갈라지지 않게 한다.
  - `record SocialIdentity(SocialProvider provider, String providerUserId, String email)`
  - `final class SocialTokenErrors`: `static BusinessException translate(JwtException e)` — `BadJwtException`이면 `SOCIAL_TOKEN_INVALID`, 원인 체인에 `com.nimbusds.jose.KeySourceException`이 있으면 `SOCIAL_PROVIDER_UNAVAILABLE`, 그 외 `SOCIAL_TOKEN_INVALID`.
  - `@Component GoogleIdTokenVerifier(@Qualifier("googleIdTokenDecoder") JwtDecoder)`: `SocialIdentity verify(String idToken)`
  - `@Component AppleIdTokenVerifier(@Qualifier("appleIdTokenDecoder") JwtDecoder)`: `SocialIdentity verify(String identityToken, String rawNonce)` — `nonce` claim과 `sha256Hex(rawNonce)`를 `MessageDigest.isEqual`로 비교, 없거나 다르면 `SOCIAL_TOKEN_INVALID`.
  - 테스트 지원 `TestSocialTokens`: RSA 키쌍 하나를 정적으로 생성. `static JwtDecoder googleDecoder(List<String> clientIds)`, `static JwtDecoder appleDecoder(String clientId)`(`NimbusJwtDecoder.withPublicKey` + 위 정적 validator), `static String google(String sub, String aud, String email)`, `static String apple(String sub, String aud, String nonceHash, String email)`, `static String expiredGoogle(String sub, String aud)`, `static String signedByOtherKey(...)`.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// GoogleIdTokenVerifierTest  (verifier = new GoogleIdTokenVerifier(TestSocialTokens.googleDecoder(List.of("ios-client","android-server-client"))))
@Test void validTokenYieldsIdentity()        // google("sub-1","ios-client","a@x.com") → (GOOGLE, "sub-1", "a@x.com")
@Test void androidServerClientAudIsAccepted()
@Test void unknownAudIsInvalid()             // aud "other" → SOCIAL_TOKEN_INVALID
@Test void expiredIsInvalid()
@Test void otherSignatureIsInvalid()
@Test void jwksFetchFailureIsProviderUnavailable() // 디코더 람다가 new JwtException("x", new RemoteKeySourceException("down", null)) 던짐 → SOCIAL_PROVIDER_UNAVAILABLE
// AppleIdTokenVerifierTest
@Test void validTokenWithMatchingNonce()     // apple("sub-a","com.app",sha256Hex("raw-n"),null) + "raw-n" → (APPLE,"sub-a",null)
@Test void nonceMismatchIsInvalid()          // rawNonce "other"
@Test void missingNonceIsInvalid()
@Test void wrongIssuerOrAudIsInvalid()
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*IdTokenVerifierTest'` → 컴파일 실패.

- [ ] **Step 3: 구현** (Interfaces 그대로).

- [ ] **Step 4: 실행해서 통과 확인** → PASS. `./gradlew test` → PASS.

- [ ] **Step 5: Commit** — `feat. 구글/애플 ID 토큰 검증기 추가`

---

### Task 7: 애플 code 교환, revoke, 토큰 암호화

(스펙 5.3 교환/revoke, 7.3)

**Files:**
- Create: `service/auth/apple/AppleTokenCipher.java`, `service/auth/apple/AppleClientSecretFactory.java`, `service/auth/apple/AppleAuthClient.java`, `service/auth/apple/HttpAppleAuthClient.java`
- Modify: `application.properties` — `oauth.apple.base-url=https://appleid.apple.com` 추가(`OAuthProperties.Apple`에 `String baseUrl` 컴포넌트 추가)
- Test: `src/test/java/com/momeokji/aiDiarybackend/service/auth/apple/{AppleTokenCipherTest,AppleClientSecretFactoryTest,HttpAppleAuthClientTest}.java`

**Interfaces:**
- Consumes: `OAuthProperties`(Task 6), `Clock`(Task 4)
- Produces:
  - `@Component AppleTokenCipher(OAuthProperties)`: `String encrypt(String plain)`, `String decrypt(String encoded)` — AES-256-GCM, IV 12바이트 랜덤, 태그 128비트, 저장 형식 `base64(iv || ciphertext)`. 키(`tokenEncryptionKey`, base64)가 32바이트가 아니면 생성 시 `IllegalStateException`(빠른 실패).
  - `@Component AppleClientSecretFactory(OAuthProperties, Clock)`: `String create()` — ES256, 헤더 `kid=keyId`, claims `iss=teamId`, `sub=clientId`, `aud="https://appleid.apple.com"`, `iat=now`, `exp=now+5분`. `.p8`(PKCS#8 PEM)은 첫 호출 때 읽고 캐시한다. 설정이 비어 있거나 파일이 없으면 `IllegalStateException`(앱 기동은 막지 않는다).
  - `interface AppleAuthClient`: `Optional<String> exchangeAuthorizationCode(String authorizationCode)`(애플 refresh 토큰, 실패 시 empty + WARN 로그), `boolean revoke(String appleRefreshToken)`(실패 시 false + ERROR 로그). 둘 다 예외를 밖으로 던지지 않는다.
  - `@Component HttpAppleAuthClient(RestClient.Builder, OAuthProperties, AppleClientSecretFactory)` — `POST {baseUrl}/auth/token`(form: `client_id`, `client_secret`, `code`, `grant_type=authorization_code`), `POST {baseUrl}/auth/revoke`(form: `client_id`, `client_secret`, `token`, `token_type_hint=refresh_token`).

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// AppleTokenCipherTest
@Test void roundTrip()                       // decrypt(encrypt("apple-rt")) == "apple-rt"
@Test void encryptionIsRandomized()          // 같은 평문 두 번 → 다른 결과
@Test void tamperedCiphertextFails()         // 한 글자 변조 → 예외
@Test void wrongKeyLengthFailsFast()
// AppleClientSecretFactoryTest  (테스트에서 EC P-256 키 생성 → @TempDir에 PKCS#8 PEM 저장)
@Test void clientSecretIsEs256WithAppleClaims() // 공개키로 서명 검증, kid/iss/sub/aud 일치, exp-iat == 300
@Test void missingKeyFileThrowsOnCreate()
// HttpAppleAuthClientTest  (MockRestServiceServer.bindTo(builder))
@Test void exchangeReturnsRefreshToken()     // /auth/token 기대 form 파라미터 확인 → {"refresh_token":"rt-1"} → Optional.of("rt-1")
@Test void exchangeFailureIsEmpty()          // 400 → Optional.empty()
@Test void revokeSendsRefreshTokenHint()     // /auth/revoke, token_type_hint=refresh_token → 200 → true
@Test void revokeFailureIsFalse()            // 500 → false
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*.apple.*'` → 컴파일 실패.

- [ ] **Step 3: 구현** (Interfaces 그대로). `application-test.properties`에 `oauth.apple.token-encryption-key`(32바이트 base64 더미)를 추가한다.

- [ ] **Step 4: 실행해서 통과 확인** → PASS. `./gradlew test` → PASS.

- [ ] **Step 5: 로컬 `.env` 안내** — 로컬 실행에도 `APPLE_TOKEN_ENCRYPTION_KEY`가 필요하다. 값 생성 명령(`openssl rand -base64 32`)을 사용자에게 알리고, `.env` 수정은 사용자 확인 후에만 한다.

- [ ] **Step 6: Commit** — `feat. 애플 authorization code 교환, revoke, refresh 토큰 암호화 추가`

---

### Task 8: Security 체인 분리와 관리자 이전

(스펙 4.4, 6.3, 7.1, 7.2, 9.1, 9.3) 기존 `JwtAuthFilter`를 Resource Server로 교체하고 관리자 API를 `/admin/**`로 옮긴다. 이 태스크가 끝나면 구 회원 API(`/users`, `/auth/login`)는 토큰을 못 받는다 — Task 10에서 삭제된다.

**Files:**
- Modify: `config/SecurityConfig.java`
- Create: `security/PublicPathBearerTokenResolver.java`, `security/MemberJwtAuthenticationConverter.java`, `security/AdminJwtAuthenticationConverter.java`, `security/ProblemDetailsAuthenticationEntryPoint.java`, `security/ProblemDetailsAccessDeniedHandler.java`
- Delete: `security/JwtAuthFilter.java`, `dto/response/AdminLoginResultDto.java`
- Modify: `controller/AdminAuthController.java`, `service/AdminAuthService.java`, `dto/response/AdminLoginResponseDto.java`
- Modify (경로만): `controller/QuestionController.java` → `/admin/questions`, `DashboardController` → `/admin/dashboards`, `MusicController` → `/admin/musics`, `FcmController` → `/admin/fcm-messages`, `FcmDebugController` → `/admin/debug/fcm`
- Test: `src/test/java/com/momeokji/aiDiarybackend/security/SecurityRulesIntegrationTest.java`, `src/test/java/com/momeokji/aiDiarybackend/controller/AdminAuthIntegrationTest.java`

**Interfaces:**
- Consumes: `memberJwtDecoder`, `adminJwtDecoder`, `AccessTokenIssuer`(Task 4), `ProblemDetailFactory`, `ErrorCode`(Task 2), `Admin.bumpTokenVersion()`(Task 3)
- Produces:
  - `PublicPathBearerTokenResolver(RequestMatcher... publicPaths)` implements `BearerTokenResolver` — 공개 경로면 `null`, 아니면 `DefaultBearerTokenResolver` 결과.
  - `MemberJwtAuthenticationConverter(MemberRepository)` implements `Converter<Jwt, AbstractAuthenticationToken>` — `role == "USER"`이고 `findByMemberIdAndIsValidTrue(sub)`가 있으면 `new JwtAuthenticationToken(jwt, [ROLE_USER], sub)`. 아니면 `InvalidBearerTokenException`.
  - `AdminJwtAuthenticationConverter(AdminRepository)` — `role ∈ {ADMIN, SUPER_ADMIN}`, `findByAdminIdAndIsValidTrue(sub)`, `deletedAt == null`, `ver == tokenVersion`이면 `ROLE_<role>` 권한. 아니면 `InvalidBearerTokenException`.
  - `ProblemDetailsAuthenticationEntryPoint(ObjectMapper)` — 코드 결정 규칙: 예외 메시지(원인 포함)에 `"Jwt expired at"`이 있으면 `TOKEN_EXPIRED`, `InvalidBearerTokenException`/`OAuth2AuthenticationException`이면 `TOKEN_INVALID`, 그 외(토큰 없음)는 `UNAUTHORIZED`. `WWW-Authenticate`는 토큰 오류면 `Bearer error="invalid_token"`, 토큰 없음이면 `Bearer`.
  - `ProblemDetailsAccessDeniedHandler(ObjectMapper)` → 403 `FORBIDDEN`.
  - `AdminLoginResponseDto { String accessToken; long accessTokenExpiresIn; Boolean isSuper; }`
  - `AdminAuthService`: `AdminLoginResponseDto login(AdminSignupRequestDto)`, `void logout(String adminId)`(→ `bumpTokenVersion`), 기존 `signup/approveAdmin/getAdmins/deleteAdmin` 유지.
  - 관리자 경로: `POST /admin/auth/signup`, `POST /admin/auth/login`, `POST /admin/auth/logout`, `POST /admin/admins/approve`, `GET /admin/admins`, `DELETE /admin/admins`.

- [ ] **Step 1: 실패하는 테스트 작성** — `IntegrationTestSupport` 상속. 관리자/회원 행은 테스트에서 직접 저장하고 토큰은 `AccessTokenIssuer`로 만든다.

```java
// SecurityRulesIntegrationTest
@Test void noTokenOnProtectedApiIsUnauthorized()       // GET /me → 401, code UNAUTHORIZED, WWW-Authenticate 헤더 존재
@Test void expiredAccessIsTokenExpired()               // 만료된 member 토큰으로 GET /me → 401 TOKEN_EXPIRED
@Test void memberTokenOnAdminApiIsTokenInvalid()       // GET /admin/admins → 401 TOKEN_INVALID
@Test void adminTokenOnAppApiIsTokenInvalid()          // GET /me → 401 TOKEN_INVALID
@Test void tokenWithoutRoleIsRejected()                // member 키로 role 없이 서명 → 401 TOKEN_INVALID
@Test void withdrawnMemberTokenIsRejected()            // is_valid=0 회원 → 401 TOKEN_INVALID
@Test void publicPathIgnoresExpiredBearer()            // 만료 토큰 붙여 GET /health → 200
@Test void oldAdminPathsAreGone()                      // 관리자 토큰으로 GET /dashboards/main → 401 (앱 체인 키 불일치)
// AdminAuthIntegrationTest
@Test void loginReturnsTokenInBody()                   // 200, $.accessToken 존재, $.accessTokenExpiresIn == 7200, $.isSuper, Authorization 응답 헤더 없음
@Test void wrongIdAndWrongPasswordLookIdentical()      // 두 경우 모두 401, code ADMIN_LOGIN_FAILED, detail 동일
@Test void pendingAdminWithRightPasswordIsNotApproved()// 403 ADMIN_NOT_APPROVED
@Test void deletedAdminCannotLogin()                   // 401 ADMIN_LOGIN_FAILED
@Test void logoutInvalidatesExistingToken()            // logout → 같은 토큰으로 GET /admin/dashboards/main → 401 TOKEN_INVALID
@Test void deletingAdminInvalidatesTheirToken()        // super가 대상 삭제 → 대상 토큰 401
@Test void nonSuperCannotApprove()                     // 기존 동작 유지: 400 BAD_REQUEST
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*SecurityRulesIntegrationTest' --tests '*AdminAuthIntegrationTest'` → FAIL.

- [ ] **Step 3: `SecurityConfig` 재작성** — 스펙 7.1 표 그대로 세 체인(Swagger `@Order(1)` 기존 유지, 관리자 `@Order(2)` `securityMatcher("/admin/**")`, 앱 `@Order(3)`). 각 체인에서 `.oauth2ResourceServer(o -> o.bearerTokenResolver(...).jwt(j -> j.decoder(...).jwtAuthenticationConverter(...)).authenticationEntryPoint(...).accessDeniedHandler(...))`, `exceptionHandling`에도 같은 entry point/handler. 공개 경로:
  - 관리자: `POST /admin/auth/signup`, `POST /admin/auth/login`
  - 앱: `POST /auth/social/**`, `POST /auth/refresh`, `GET /health`, `/error`
  - 앱 체인 기본값 `anyRequest().hasRole("USER")`, 관리자 체인 `anyRequest().hasAnyRole("ADMIN","SUPER_ADMIN")`. CORS, STATELESS, CSRF disable은 기존 그대로. `PasswordEncoder`, `CorsConfigurationSource` 빈 유지. CORS exposed headers에서 `Refresh-Token` 제거.

- [ ] **Step 4: 나머지 구현** — 컨버터, entry point, 관리자 서비스/컨트롤러/경로 변경, `JwtAuthFilter` 삭제. 관리자 로그인 순서: `findByAdminId` 없음 또는 `deletedAt != null` 또는 비밀번호 불일치 → `ADMIN_LOGIN_FAILED`; 그다음 `!isValid` → `ADMIN_NOT_APPROVED`.

- [ ] **Step 5: 실행해서 통과 확인** — 같은 명령 → PASS. `./gradlew test` → PASS.

- [ ] **Step 6: Commit** — `feat. Resource Server 기반 앱/관리자 체인 분리와 관리자 API /admin 이전`

---

### Task 9: 기기별 푸시 발송

(스펙 8.3) 회원 단위 FCM 토큰을 기기 단위로 바꾼다. `Member.deviceId` 의존을 없애 Task 10의 필드 삭제를 가능하게 한다.

**Files:**
- Create: `service/push/FcmSender.java`, `service/push/FirebaseFcmSender.java`
- Modify: `service/FcmService.java`, `scheduler/DiaryRemindScheduler.java`, `scheduler/DiarySympathyScheduler.java`, `controller/FcmDebugController.java`
- Modify: `src/test/java/.../support/IntegrationTestSupport.java` — `@MockitoBean protected FcmSender fcmSender`
- Test: `src/test/java/com/momeokji/aiDiarybackend/service/FcmServiceTest.java`

**Interfaces:**
- Consumes: `MemberDeviceRepository`(Task 3)
- Produces:
  - `interface FcmSender { String send(Message message) throws FirebaseMessagingException; }`, `@Component FirebaseFcmSender` — `FirebaseMessaging.getInstance().send(message)`.
  - `FcmService(MemberDeviceRepository, FcmSender)`:
    - `void sendToMember(String memberId, String title, String body, Map<String,String> data)` — 그 회원의 `fcm_token` 있는 모든 기기로 발송. OS 분기(`AND`/`IOS`)는 기존 메시지 구성 그대로, 기준만 `device.getOs()`.
    - `void sendNotificationToAllMembers(String title, String body)` — `findByFcmTokenIsNotNull()` 전체. 성공/실패/건수 로그는 기존과 같은 형태.
    - `void remindNotification(String memberId)`, `void feedbackNotification(String memberId, Long diaryId)` — 문구와 data는 기존 그대로, **`throws FirebaseMessagingException` 제거**.
    - 발송 예외: `FirebaseMessagingException.getMessagingErrorCode()`가 `UNREGISTERED` 또는 `INVALID_ARGUMENT`면 `clearFcmToken(token)`; 그 외는 로그만. 한 기기 실패가 다른 기기 발송을 막지 않는다.
  - `DiaryRemindScheduler`: `member.getDeviceId()` 확인을 `memberDeviceRepository.existsPushableDevice(memberId)`로 교체. `FirebaseMessagingException` catch 제거.
  - `DiarySympathyScheduler`, `FcmDebugController`: `FirebaseMessagingException` catch/throws 제거.

- [ ] **Step 1: 실패하는 테스트 작성** — `@DataJpaTest` + `FcmSender`는 Mockito mock, 서비스 직접 생성.

```java
@Test void sendsToEveryDeviceOfMember()          // m1 기기 2대(fcm A,B), m2 1대 → sendToMember(m1) → send 2회, 토큰 {A,B}
@Test void skipsDevicesWithoutFcmToken()
@Test void unregisteredTokenIsCleared()          // A 발송이 UNREGISTERED 예외 → A 행 fcm == null, B는 정상 발송
@Test void otherFailureKeepsToken()              // INTERNAL 예외 → fcm 유지
@Test void iosDeviceGetsApnsNotification()       // os IOS → Message에 notification/apns 설정 (ArgumentCaptor로 확인)
@Test void broadcastHitsAllPushableDevices()
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*FcmServiceTest'` → 컴파일 실패.

- [ ] **Step 3: 구현** (Interfaces 그대로). `FirebaseMessagingException`은 public 생성자가 없으므로 테스트에서 `Mockito.mock(FirebaseMessagingException.class)` + `getMessagingErrorCode()` 스텁으로 만든다.

- [ ] **Step 4: 실행해서 통과 확인** → PASS. `./gradlew test` → PASS.

- [ ] **Step 5: Commit** — `refactor. FCM 발송을 회원 단위에서 기기 단위로 변경`

---

### Task 10: 앱 인증 API와 구 인증 코드 삭제

(스펙 6.1, 6.2 `GET /me`, 6.4, 8.2, 10장)

**Files:**
- Create: `service/auth/SocialLoginResult.java`, `service/auth/SocialLoginService.java`
- Create: `dto/request/GoogleLoginRequestDto.java`, `dto/request/AppleLoginRequestDto.java`, `dto/request/RefreshTokenRequestDto.java`, `dto/request/LogoutRequestDto.java`, `dto/request/FcmTokenUpdateRequestDto.java`, `dto/response/AuthTokenResponseDto.java`
- Modify: `controller/AuthController.java`, `service/AuthService.java`, `entity/Member.java`, `repository/MemberRepository.java`, `service/DiaryService.java`
- Delete: `common/util/JwtUtil.java`, `scheduler/InactiveMemberScheduler.java`, `dto/request/MemberSignupRequestDto.java`, `dto/request/MemberLoginRequestDto.java`, `dto/response/TokenResponseDto.java`
- Modify: `build.gradle` — jjwt 3개 제거
- Modify: `application.properties`, `application-test.properties` — `jwt.secret`, `jwt.expiration.*` 제거(새 `jwt.member.secret=${JWT_SECRET}`가 대체)
- Modify: `src/test/java/.../support/IntegrationTestSupport.java` — `@Import(TestSocialConfig.class)`
- Create: `src/test/java/com/momeokji/aiDiarybackend/support/TestSocialConfig.java` — `@TestConfiguration`: 빈 이름 `googleIdTokenDecoder`/`appleIdTokenDecoder`를 `TestSocialTokens` 디코더로 덮어쓰고, `AppleAuthClient`를 `@Primary FakeAppleAuthClient`(교환 결과 지정, revoke 호출 기록)로 대체
- Test: `src/test/java/com/momeokji/aiDiarybackend/controller/AuthIntegrationTest.java`, `src/test/java/com/momeokji/aiDiarybackend/service/auth/SocialLoginServiceTest.java`

**Interfaces:**
- Consumes: `GoogleIdTokenVerifier`, `AppleIdTokenVerifier`, `SocialIdentity`(Task 6), `AppleAuthClient`, `AppleTokenCipher`(Task 7), `DeviceSessionService`, `IssuedTokens`(Task 5), `SocialAccountRepository`(Task 3), `RedisService.seedDefaultsIfEmpty`, `RefsetSeedLoader`(기존)
- Produces:
  - `record SocialLoginResult(IssuedTokens tokens, boolean newMember)`
  - `SocialLoginService`: `SocialLoginResult loginWithGoogle(GoogleLoginRequestDto)`, `SocialLoginResult loginWithApple(AppleLoginRequestDto)`. 공통 흐름은 스펙 6.1 "소셜 로그인 처리 순서" 1~5. 이 두 메서드 자체는 트랜잭션을 걸지 않는다 — 소셜 검증과 애플 HTTP 호출이 DB 트랜잭션 밖에서 일어나게 하기 위해서다. 회원 조회/생성은 별도 빈 메서드(`REQUIRES_NEW`)에서 하고, `DataIntegrityViolationException`이면 `findByProviderAndProviderUserId`로 재조회해 기존 회원으로 처리한다. 애플은 `appleRefreshToken`이 없을 때만 code 교환을 시도한다. email은 `updateEmailIfPresent`로만 갱신한다.
  - `Member`: `deviceId`, `mobileOS`, `preference`, `lastActiveAt` 필드 제거. 생성은 `static Member create(String memberId)`(빌더 제거).
  - `MemberRepository`: `findByDeviceId`, `findByDeviceIdAndIsValidTrue`, `findByIsValidTrueAndLastActiveAtBefore`, `changeLastActiveAt` 제거.
  - `DiaryService.updateLastActiveAt` 제거.
  - DTO (모두 `@Getter`, Bean Validation):
    - `GoogleLoginRequestDto { @NotBlank idToken; @NotBlank @Pattern(UUID) deviceId; @NotBlank @Pattern("AND|IOS") os; fcmToken; }`
    - `AppleLoginRequestDto { @NotBlank identityToken; @NotBlank authorizationCode; @NotBlank nonce; @NotBlank @Pattern(UUID) deviceId; @NotBlank @Pattern("AND|IOS") os; fcmToken; }`
    - `RefreshTokenRequestDto { @NotBlank refreshToken; @NotBlank @Pattern(UUID) deviceId; }`
    - `LogoutRequestDto { @NotBlank @Pattern(UUID) deviceId; }`
    - `FcmTokenUpdateRequestDto { @NotBlank @Pattern(UUID) deviceId; @NotBlank fcmToken; }`
    - UUID 패턴: `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`
    - `AuthTokenResponseDto { accessToken; long accessTokenExpiresIn; refreshToken; long refreshTokenExpiresIn; @JsonInclude(NON_NULL) Boolean isNewMember; }` — `static from(IssuedTokens)`, `static from(SocialLoginResult)`
  - `AuthController` 엔드포인트: 스펙 6.1 그대로 + `GET /me`, `DELETE /users`, `GET /health` 유지. 로그아웃·FCM 갱신은 `auth.getName()`을 `memberId`로 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// AuthIntegrationTest (IntegrationTestSupport 상속, 소셜 토큰은 TestSocialTokens로 생성)
@Test void firstGoogleLoginCreatesMember()            // 200, isNewMember true, refreshTokenExpiresIn 5184000; GET /me with access → 200
@Test void secondGoogleLoginReusesMember()            // isNewMember false, /me 결과 동일
@Test void googleAndAppleAreSeparateMembers()
@Test void refreshReturnsNewPair()                     // POST /auth/refresh → 200, 새 refreshToken != 이전 값, 새 access로 GET /me 200
@Test void refreshAfterLogoutIsInvalid()               // logout 후 같은 refresh → 401 REFRESH_INVALID
@Test void refreshWithExpiredAccessHeaderStillWorks() // 만료 Bearer 붙여 POST /auth/refresh → 200
@Test void logoutReturns204EvenTwice()
@Test void fcmTokenUpdateOnForeignDeviceIs404()       // code DEVICE_NOT_FOUND
@Test void invalidDeviceIdOrOsIsValidationFailed()    // deviceId "abc" / os "WEB" → 400 VALIDATION_FAILED
@Test void appleLoginStoresEncryptedRefreshToken()    // Fake 교환 "apple-rt" → social_account.apple_refresh_token != "apple-rt", cipher.decrypt == "apple-rt"
@Test void appleCodeExchangeFailureStillLogsIn()      // Fake 교환 empty → 200, 저장값 null; 재로그인 시 교환 재시도
@Test void appleReloginWithoutEmailKeepsStoredEmail()
@Test void appleNonceMismatchIs401()                  // code SOCIAL_TOKEN_INVALID
@Test void removedEndpointsAreGone()                  // POST /users, POST /auth/login → 401(무인증) 또는 404, 200이 아니어야 함
@Test void newMemberGetsRefsetSeed()                  // verify(redisService).seedDefaultsIfEmpty(eq(memberId), anyList())
// SocialLoginServiceTest (Mockito 단위)
@Test void concurrentFirstLoginFallsBackToExistingAccount() // 생성 메서드가 DataIntegrityViolationException → 재조회 결과의 memberId로 openSession 호출, newMember false
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*AuthIntegrationTest' --tests '*SocialLoginServiceTest'` → FAIL.

- [ ] **Step 3: 신규 API 구현** — `SocialLoginService`, DTO, `AuthController` 재작성, `TestSocialConfig`.

- [ ] **Step 4: 구 코드 삭제** — Files의 Delete/Modify 목록대로. Task 3·5·8·9 테스트에서 `Member.builder()`를 쓰던 곳은 `Member.create(...)`로 바꾼다. 삭제 후 `grep -rn "JwtUtil\|getDeviceId\|getMobileOS\|lastActiveAt\|io.jsonwebtoken" src/main` → 결과 없음.

- [ ] **Step 5: 실행해서 통과 확인** — 같은 명령 → PASS. `./gradlew test` → PASS.

- [ ] **Step 6: Commit** — `feat. 구글/애플 소셜 로그인과 기기 세션 API 추가, 기기 기반 인증 제거`

---

### Task 11: 탈퇴 확장

(스펙 6.2 `DELETE /users`)

**Files:**
- Modify: `service/AuthService.java`
- Test: `src/test/java/com/momeokji/aiDiarybackend/controller/WithdrawIntegrationTest.java`

**Interfaces:**
- Consumes: `SocialAccountRepository`(Task 3), `DeviceSessionService.deleteAllOf`(Task 5), `AppleAuthClient.revoke`, `AppleTokenCipher.decrypt`(Task 7)
- Produces: `AuthService.withdrawByUserId(String memberId)` — 순서: ① 그 회원의 APPLE 계정에 저장된 토큰이 있으면 복호화 후 `revoke`(false여도 계속) ② `socialAccountRepository.deleteByMemberId` ③ `deviceSessionService.deleteAllOf` ④ 기존 일기/색상/이미지 soft delete, 회원 soft delete, `redisService.deleteUserData`. `withdraw(Authentication)`은 그대로 위임.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
@Test void withdrawRemovesSocialAndDevicesAndSoftDeletesMember() // DELETE /users → 204; social_account/member_device 0건; member.is_valid == false; 기존 access로 GET /me → 401 TOKEN_INVALID
@Test void reloginAfterWithdrawCreatesNewMember()                 // 같은 sub로 로그인 → isNewMember true, memberId 다름
@Test void appleWithdrawRevokesStoredToken()                      // FakeAppleAuthClient.revoked == ["apple-rt"]
@Test void appleRevokeFailureStillWithdraws()                     // Fake revoke false → 204, 삭제 완료
@Test void oldRefreshTokenIsInvalidAfterWithdraw()                // POST /auth/refresh → 401 REFRESH_INVALID
```

- [ ] **Step 2: 실행해서 실패 확인** — `./gradlew test --tests '*WithdrawIntegrationTest'` → FAIL.

- [ ] **Step 3: 구현** (Interfaces 그대로).

- [ ] **Step 4: 실행해서 통과 확인** → PASS. `./gradlew test` → PASS.

- [ ] **Step 5: Commit** — `feat. 탈퇴 시 소셜 계정·기기 세션 삭제와 애플 revoke 처리`

---

### Task 12: 마무리 (정리 SQL, 문서, 설정)

(스펙 8.1, 10장, 13장)

**Files:**
- Create: `docs/db/2026-10-19-cleanup-anonymous-members.sql`
- Modify: `config/OpenApiConfig.java`
- Modify: `src/main/resources/application.properties` (최종 점검)

- [ ] **Step 1: 정리 SQL 작성** — 한 트랜잭션에서 `is_valid = 1`인 기존 `member`의 `diary_color`, `diary_image`, `diary`(조인 기준 `diary.user_id = member.member_id`)와 `member`를 `is_valid = 0, deleted_at = NOW()`로. 실행 전 건수 확인용 `SELECT COUNT(*)` 쿼리를 주석 위에 함께 둔다. 주석으로: "새 서버 배포 직후, 소셜 로그인 회원이 생기기 전에 1회 실행", Redis 정리 명령은 실제 키 패턴을 `RedisService`에서 확인해 `redis-cli --scan --pattern '<패턴>' | xargs redis-cli del` 형태로 적는다.

- [ ] **Step 2: `OpenApiConfig` 수정** — `refreshToken` 보안 스킴과 그 설명을 제거하고, 설명 문구를 "소셜 로그인 또는 `/auth/refresh` 응답 바디의 accessToken"으로 바꾼다. 관리자 토큰도 같은 Bearer 스킴을 쓴다는 한 줄을 추가한다.

- [ ] **Step 3: 설정 점검** — `application.properties`에 스펙 10장 키가 모두 있고 `JWT_EXPIRATION_*` 참조가 없는지 확인: `grep -n "jwt\.\|oauth\." src/main/resources/application.properties`.

- [ ] **Step 4: 전체 검증** — `./gradlew clean build` → BUILD SUCCESSFUL(테스트 포함). 이어서 `.env`에 새 키(`JWT_ADMIN_SECRET`, `APPLE_TOKEN_ENCRYPTION_KEY`)가 있는 상태에서 `./run-local.sh`(또는 `./gradlew bootRun --args='--spring.profiles.active=local'`)로 기동 → 로컬 DB에 `docs/db/2026-10-19-social-login-schema.sql`을 먼저 적용해야 `validate`가 통과한다. 기동 후 `curl -s localhost:8080/health` → `OK`, `curl -s -o /dev/null -w '%{http_code}' localhost:8080/me` → `401`. 로컬 DB 적용과 `.env` 수정은 사용자 확인 후에 한다.

- [ ] **Step 5: Commit** — `docs. 익명 회원 정리 SQL 추가, Swagger 인증 설명 갱신`
