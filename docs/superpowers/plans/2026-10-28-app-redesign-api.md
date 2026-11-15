# 앱 리디자인 대응 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 앱 리디자인이 요구하는 필드·엔드포인트(일기 타입, 통계, 알림 토글, 정렬·페이지네이션, 색상 이름, 음악 길이, 최소 버전)를 KST 기준으로 일관되게 제공하고 에러 코드를 문서화한다.

**Architecture:** 기존 레이어 구조(`controller/service/repository/entity/dto`)를 그대로 따른다. 날짜 경계는 `Clock` 빈(KST)으로만 구한다. 통계·일일 제한은 `DiaryService`에서 분리한 `DiaryStatsService`가 맡는다. 응답 DTO는 `LocalDateTime` 대신 `OffsetDateTime`을 내려 `+09:00`이 붙는다.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring Data JPA (MySQL 운영 / H2 테스트), JUnit 5 + MockMvc + Mockito.

**Spec:** `docs/superpowers/specs/2026-10-28-app-redesign-api-design.md` — 각 태스크는 스펙 3장의 항목 번호를 근거로 한다.

## Global Constraints

- 베이스 패키지 `com.momeokji.aiDiarybackend`. 탭 들여쓰기, Lombok, 한국어 주석·메시지.
- 타임존은 `Asia/Seoul` 하나. 상수 `KstTime.ZONE = ZoneId.of("Asia/Seoul")`를 쓰고 `ZoneId.of(...)`를 다른 곳에서 새로 만들지 않는다.
- 에러는 `BusinessException(ErrorCode)` → RFC 9457 `code`. 신규 코드: `DIARY_NOT_FOUND`(404), `DIARY_DAILY_LIMIT_EXCEEDED`(429).
- DDL은 `docs/db/2026-10-28-app-redesign-api.sql` 한 파일에 누적한다. 엔티티 매핑과 타입을 맞춘다(`ddl-auto=validate`).
- 일일 제한 값은 `diary.daily-limit` 프로퍼티, 기본 3.
- 응답 `createdAt`은 `OffsetDateTime`. 변환은 `KstTime.toOffset(LocalDateTime)`.
- 정렬 값 `recent`(기본) / `oldest` / `created`(= oldest 별칭). 그 외 400 `BAD_REQUEST`.
- git push 금지. 커밋 메시지는 `feat. / fix. / refactor. / test. / docs.` 접두사, 끝에 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- 테스트: `./gradlew test --tests '<FQCN>'`. 전체: `./gradlew test`.

## Review Focus

1. KST 자정 경계: 어제 23:59:59 일기와 오늘 00:00:00 일기가 있으면 `todayCount=1`, `streakDays=2`. → Task 7 `todayCountRespectsKstMidnight`, `streakCountsConsecutiveDays`
2. 삭제한 일기는 일일 제한에 세지 않는다: 3개 쓰고 1개 지우면 다시 쓸 수 있다. → Task 7 `deletedDiaryDoesNotCountTowardLimit`
3. `sort=weird` → 400 `BAD_REQUEST`, 500이 아니다. → Task 4 `unknownSortIsBadRequest`
4. 색상 프롬프트가 아직 구형(`{"colors":"#AABBCC, #DDEEFF"}`)이어도 저장은 되고 `name`은 null. → Task 5 `legacyStringFormatGivesNullNames`
5. 알림 기본 꺼짐: 토글을 켜지 않은 회원에게 리마인드가 가지 않는다. → Task 8 `remindSkipsMembersWithNotificationOff`
6. 만료된 access 토큰을 붙인 채 `GET /app/min-version`을 불러도 200. → Task 9 `minVersionIgnoresBearerToken`

---

## File Map

| 파일 | 책임 | 태스크 |
|---|---|---|
| `AiDiarybackendApplication.java`, `config/ClockConfig.java`, `common/time/KstTime.java`, `application.properties` | 타임존 고정, 오프셋 변환 | 1 |
| `dto/response/{DiaryConfirm,DiaryDetail,DiaryList}ResponseDto.java` | `createdAt` 오프셋, `type`, `colors` 객체, `music.durationSeconds` | 1, 3, 5, 6 |
| `support/IntegrationTestSupport.java`, `support/DiaryTestSupport.java` | 회원 로그인·일기 삽입 헬퍼 | 1 |
| `common/error/ErrorCode.java`, `service/DiaryService.java` | 404 `DIARY_NOT_FOUND` | 2 |
| `entity/{DiaryType,Diary}.java`, `docs/db/2026-10-28-app-redesign-api.sql` | 일기 타입 | 3 |
| `dto/DiarySort.java`, `dto/response/DiaryPageResponseDto.java`, `controller/DiaryController.java`, `repository/DiaryRepository.java` | 정렬, 페이지 메타 | 4 |
| `entity/DiaryColor.java`, `service/DiaryColorService.java`, `dto/response/ColorDto.java` | 색상 이름 | 5 |
| `entity/Music.java`, `dto/request/Music{Create,Update}Request.java`, `dto/response/MusicResponse.java`, `service/MusicService.java`, `scripts/backfill-music-duration.sh` | 음악 길이 | 6 |
| `service/DiaryStatsService.java`, `dto/response/DiaryStatsResponseDto.java`, `controller/UserController.java` | 통계, 일일 제한 | 7 |
| `entity/Member.java`, `dto/request/NotificationUpdateRequestDto.java`, `dto/response/{UserMe,Notification}ResponseDto.java`, `service/UserService.java`, `repository/MemberRepository.java`, `scheduler/*` | provider, 알림 토글 | 8 |
| `entity/AppVersion.java`, `repository/AppVersionRepository.java`, `service/AppVersionService.java`, `controller/{AppVersion,AdminAppVersion}Controller.java`, `dto/*AppVersion*`, `config/SecurityConfig.java` | 최소 버전 | 9 |
| `config/OpenApiConfig.java`, 컨트롤러 `@Operation` | 에러 코드 표, 닉네임 규칙, 문답 건너뛰기 | 10 |

---

### Task 1: 타임존 고정과 `createdAt` 오프셋 (스펙 16)

**Files:**
- Modify: `src/main/java/com/momeokji/aiDiarybackend/AiDiarybackendApplication.java`
- Modify: `src/main/java/com/momeokji/aiDiarybackend/config/ClockConfig.java`
- Create: `src/main/java/com/momeokji/aiDiarybackend/common/time/KstTime.java`
- Modify: `src/main/resources/application.properties`
- Modify: `dto/response/DiaryConfirmResponseDto.java`, `DiaryDetailResponseDto.java`, `DiaryListResponseDto.java` (`createdAt` 타입)
- Modify: `service/DiaryService.java` (빌더에 `KstTime.toOffset(diary.getCreatedAt())`)
- Modify: `src/test/java/.../support/IntegrationTestSupport.java`
- Create: `src/test/java/.../support/DiaryTestSupport.java`
- Test: `src/test/java/.../common/time/KstTimeTest.java`, `src/test/java/.../controller/DiaryIntegrationTest.java`

**Interfaces:**
- Produces: `KstTime.ZONE : ZoneId`, `KstTime.toOffset(LocalDateTime) : OffsetDateTime` (null → null)
- Produces: `IntegrationTestSupport.loginAsMember(String sub) : String` (access token; `deviceId` 고정 UUID, os `IOS`)
- Produces: `DiaryTestSupport extends IntegrationTestSupport`: `@MockitoBean OpenAiService openAiService`, `@MockitoBean DiaryImageService diaryImageService`, `memberIdOf(String token)`, `insertDiary(String memberId, LocalDateTime createdAt, DiaryType type) : Diary` (Task 3 전까지는 `type` 파라미터 없이 만들고 Task 3에서 추가), `stubOpenAi()` — `openAiService.call("summary", any)`→`{"summary":"요약"}`, `"colors"`→`{"colors":"#C3E9A6, #FFFFFF"}`, `"music"`→`{"mood":"none"}`, `"content"`→`"생성된 일기"`

- [ ] **Step 1: `KstTimeTest` 작성**

```java
@Test void toOffsetAttachesPlusNine() {
	OffsetDateTime out = KstTime.toOffset(LocalDateTime.of(2025, 9, 16, 21, 12));
	assertEquals("2025-09-16T21:12+09:00", out.toString());
}
@Test void nullStaysNull() { assertNull(KstTime.toOffset(null)); }
```

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*KstTimeTest'` → 컴파일 실패

- [ ] **Step 3: `KstTime` 구현, `AiDiarybackendApplication`에 `static { TimeZone.setDefault(TimeZone.getTimeZone(KstTime.ZONE)); }`, `ClockConfig.clock()`을 `Clock.system(KstTime.ZONE)`으로, `application.properties`에 `spring.jackson.time-zone=Asia/Seoul`**

- [ ] **Step 4: 세 DTO의 `createdAt`을 `OffsetDateTime`으로 바꾸고 `DiaryService`의 세 빌더 호출을 `KstTime.toOffset(...)`으로 수정**

- [ ] **Step 5: 테스트 헬퍼 작성** — `IntegrationTestSupport.loginAsMember`(UserIntegrationTest의 `login` 로직 이동, 기존 테스트는 손대지 않음), `DiaryTestSupport`

- [ ] **Step 6: `DiaryIntegrationTest.createdAtHasKstOffset` 작성**

```java
@Test void createdAtHasKstOffset() throws Exception {
	String token = loginAsMember("sub-1");
	Diary d = insertDiary(memberIdOf(token), LocalDateTime.of(2025, 9, 16, 21, 12));
	mockMvc.perform(get("/diaries/" + d.getDiaryId()).header(AUTHORIZATION, "Bearer " + token))
		.andExpect(status().isOk())
		.andExpect(jsonPath("$.createdAt").value("2025-09-16T21:12:00+09:00"));
}
```

- [ ] **Step 7: 통과 확인** — `./gradlew test --tests '*KstTimeTest' --tests '*DiaryIntegrationTest'` → PASS. `spring.jackson.serialization.write-dates-as-timestamps`가 기본 false인지 확인(실패하면 properties에 명시).

- [ ] **Step 8: 전체 테스트** — `./gradlew test` → PASS (기존 `DiaryRemindSchedulerTest` 등 영향 없음)

- [ ] **Step 9: Commit** — `feat. 서버 타임존 KST 고정, 일기 createdAt 에 +09:00 오프셋`

---

### Task 2: `DIARY_NOT_FOUND` (스펙 13)

**Files:**
- Modify: `common/error/ErrorCode.java`
- Modify: `service/DiaryService.java` (`getDetail`, `deleteDiary`, `updateContent`의 `orElseThrow`)
- Test: `controller/DiaryIntegrationTest.java`

**Interfaces:**
- Produces: `ErrorCode.DIARY_NOT_FOUND(NOT_FOUND, "일기를 찾을 수 없습니다.")`, `ErrorCode.DIARY_DAILY_LIMIT_EXCEEDED(TOO_MANY_REQUESTS, "오늘 작성할 수 있는 일기 개수를 넘었습니다.")`

- [ ] **Step 1: 테스트 작성**

```java
@Test void missingDiaryIs404() throws Exception {
	String token = loginAsMember("sub-1");
	mockMvc.perform(get("/diaries/999").header(AUTHORIZATION, "Bearer " + token))
		.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"));
	mockMvc.perform(delete("/diaries/999").header(AUTHORIZATION, "Bearer " + token))
		.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"));
}
@Test void otherMembersDiaryIs404() throws Exception { /* sub-1 의 일기를 sub-2 토큰으로 GET → 404 */ }
```

- [ ] **Step 2: 실패 확인** — 400 이 나온다
- [ ] **Step 3: `ErrorCode` 두 개 추가, `DiaryService` 세 곳을 `new BusinessException(ErrorCode.DIARY_NOT_FOUND)`로**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: Commit** — `fix. 일기 없음을 400 대신 404 DIARY_NOT_FOUND 로 응답`

---

### Task 3: 일기 타입 (스펙 1)

**Files:**
- Create: `entity/DiaryType.java` (`FREE`, `QNA`)
- Modify: `entity/Diary.java`, `service/DiaryService.java`, 세 응답 DTO
- Create: `docs/db/2026-10-28-app-redesign-api.sql`
- Modify: `support/DiaryTestSupport.java` (`insertDiary`에 `type` 추가)
- Test: `controller/DiaryIntegrationTest.java`

**Interfaces:**
- Produces: `Diary.type : DiaryType` (`@Enumerated(STRING) @Column(name="type", nullable=false, length=10)`, 빌더 기본값 `FREE`)
- Produces: 세 DTO에 `private DiaryType type;`
- Produces: `DiaryService.doConfirm(..., DiaryType type)` — `confirm`은 `QNA`, `confirmFree`는 `FREE`

- [ ] **Step 1: 테스트 작성**

```java
@Test void freeDiaryHasTypeFree() throws Exception {
	stubOpenAi();
	String token = loginAsMember("sub-1");
	mockMvc.perform(multipart("/diaries/free").part(new MockPart("text", "자유 일기".getBytes(UTF_8)))
			.header(AUTHORIZATION, "Bearer " + token))
		.andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("FREE"));
}
@Test void qnaDiaryHasTypeQna() throws Exception { /* POST /diaries → $.type == "QNA"; GET /diaries/{id} → $.type == "QNA" */ }
```

- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — enum, 엔티티 필드, `doConfirm` 시그니처, DTO 필드, 빌더 매핑(`getDetail`, `getList`, `getCalendar`, `doConfirm`)
- [ ] **Step 4: DDL 파일 생성** — 헤더 주석(실행 시점: 배포 전)과 `ALTER TABLE diary ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'FREE';`
- [ ] **Step 5: 통과 확인 + 전체 테스트**
- [ ] **Step 6: Commit** — `feat. 일기 타입(FREE/QNA) 저장·응답`

---

### Task 4: 정렬과 페이지네이션 메타 (스펙 7, 8)

**Files:**
- Create: `dto/DiarySort.java`
- Create: `dto/response/DiaryPageResponseDto.java`
- Modify: `controller/DiaryController.java`, `service/DiaryService.java`, `repository/DiaryRepository.java`
- Test: `controller/DiaryIntegrationTest.java`

**Interfaces:**
- Produces: `enum DiarySort { RECENT, OLDEST; static DiarySort parse(String raw) }` — null/빈 값 → `RECENT`, `recent`/`oldest`/`created`(대소문자 무시), 그 외 `IllegalArgumentException("sort 는 recent 또는 oldest 여야 합니다.")`; `Sort.Direction direction()`
- Produces: `DiaryPageResponseDto { List<DiaryListResponseDto> items; int page; int size; boolean hasNext; long totalCount; }`
- Produces: `DiaryService.getList(Authentication, String sort, Integer page, Integer size) : DiaryPageResponseDto`
- Produces: `DiaryService.getCalendar(Authentication, Integer year, Integer month, String sort) : List<DiaryListResponseDto>`
- Produces: `DiaryRepository.findByUserIdAndIsValidTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String, LocalDateTime, LocalDateTime, Sort)` (기존 `...OrderByCreatedAtAsc` 메서드는 삭제)

- [ ] **Step 1: 테스트 작성**

```java
@Test void listReturnsPageMeta() throws Exception {
	// 일기 3개 삽입 후 GET /diaries?size=2 → items.length 2, hasNext true, totalCount 3, page 0, size 2
	// GET /diaries?size=2&page=1 → items.length 1, hasNext false
}
@Test void sortOldestAndCreatedAlias() throws Exception {
	// createdAt 1일, 2일, 3일 삽입. sort=oldest 와 sort=created 모두 items[0].diaryId == 첫 번째, 기본은 마지막
}
@Test void calendarAcceptsSort() throws Exception {
	// GET /diaries/calendar?year&month&sort=oldest → [0] 이 가장 오래된 것, 기본(recent) → [0] 이 최신
}
@Test void unknownSortIsBadRequest() throws Exception {
	// GET /diaries?sort=weird → 400, $.code == "BAD_REQUEST"
}
```

- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — `getList`와 `getCalendar`의 중복 색상·음악 매핑을 `private List<DiaryListResponseDto> toListDtos(List<Diary>)`로 뽑아 공유한다.
- [ ] **Step 4: 통과 확인 + 전체 테스트**
- [ ] **Step 5: Commit** — `feat. 일기 목록 페이지 메타(hasNext/totalCount), sort=recent|oldest, 캘린더 정렬`

---

### Task 5: 색상 이름 (스펙 9)

**Files:**
- Modify: `entity/DiaryColor.java` (`@Column(name="name", length=30) String name`)
- Create: `dto/response/ColorDto.java` (`String name; String hex;` + `static from(DiaryColor)`)
- Modify: `service/DiaryColorService.java`, `service/DiaryService.java`, 세 응답 DTO(`List<ColorDto> colors`)
- Modify: `docs/db/2026-10-28-app-redesign-api.sql`
- Test: `service/DiaryColorServiceTest.java`, `controller/DiaryIntegrationTest.java`

**Interfaces:**
- Produces: `record DiaryColorService.ColorPick(String name, String hex)`
- Produces: `DiaryColorService.parseColors(String raw) : List<ColorPick>` — 기존 `parseHexColors`는 삭제
- Produces: `DiaryColor.name`, `ColorDto.from(DiaryColor)`

- [ ] **Step 1: 단위 테스트 작성**

```java
@Test void objectFormatGivesNameAndHex() {
	var out = service.parseColors("```json\n{\"colors\":[{\"name\":\"새싹 연두\",\"hex\":\"#c3e9a6\"},{\"name\":\"하늘\",\"hex\":\"#AABBCC\"}]}\n```");
	assertEquals(List.of(new ColorPick("새싹 연두", "#C3E9A6"), new ColorPick("하늘", "#AABBCC")), out);
}
@Test void legacyStringFormatGivesNullNames() {
	assertEquals(List.of(new ColorPick(null, "#AABBCC"), new ColorPick(null, "#DDEEFF")), service.parseColors("{\"colors\":\"#AABBCC, #DDEEFF\"}"));
}
@Test void bareTextGivesNullNames() { /* "색은 #112233 입니다" → [(null, "#112233")] */ }
@Test void objectWithoutValidHexIsSkipped() { /* [{"name":"x","hex":"red"},{"name":"y","hex":"#000000"}] → [(y,#000000)] */ }
@Test void noHexThrows() { assertThrows(IllegalStateException.class, () -> service.parseColors("없음")); }
```

- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — 코드펜스 제거 후 JSON 파싱; `colors`가 배열이면 각 원소의 `hex`가 `#[0-9A-Fa-f]{6}`에 맞을 때만 채택; 문자열이거나 JSON이 아니면 기존 hex 추출 경로. `DiaryService.doConfirm`은 `name`을 `DiaryColor` 빌더에 넣고, 응답 매핑 세 곳은 `ColorDto.from`.
- [ ] **Step 4: 통합 테스트 추가** — `stubOpenAi` 기본값(구형 문자열)으로 `/diaries/free` → `$.colors[0].hex == "#C3E9A6"`, `$.colors[0].name` null. 그리고 `openAiService.call(eq("colors"), any())`를 객체 형식으로 바꿔 `$.colors[0].name == "새싹 연두"`, 상세 조회에서도 동일.
- [ ] **Step 5: DDL 추가** — `ALTER TABLE diary_color ADD COLUMN name VARCHAR(30) NULL;`
- [ ] **Step 6: 통과 확인 + 전체 테스트**
- [ ] **Step 7: Commit** — `feat. 일기 색상에 이름 추가, colors 를 {name, hex} 객체 배열로`

---

### Task 6: 음악 길이 (스펙 10)

**Files:**
- Modify: `entity/Music.java` (`@Column(name="duration_seconds") Integer durationSeconds`)
- Modify: `dto/request/MusicCreateRequest.java`, `MusicUpdateRequest.java` (`@Positive Integer durationSeconds`)
- Modify: `dto/response/MusicResponse.java`, 세 응답 DTO의 `MusicDto`(`Integer durationSeconds`), `service/MusicService.java`, `service/DiaryService.java`
- Create: `scripts/backfill-music-duration.sh`
- Modify: `docs/db/2026-10-28-app-redesign-api.sql`
- Test: `service/MusicServiceTest.java`, `controller/DiaryIntegrationTest.java`

- [ ] **Step 1: 테스트 작성**

```java
// MusicServiceTest (mock MusicRepository, OpenAiService)
@Test void createStoresDurationSeconds() { /* request durationSeconds 182 → 저장된 Music.durationSeconds 182, 응답 182 */ }
@Test void updateChangesDurationOnlyWhenGiven() { /* null 이면 유지, 200 이면 200 */ }
// DiaryIntegrationTest
@Test void detailMusicHasDurationSeconds() { /* musicRepository.save(duration 182) 후 insertDiary(recommandMusic=id) → $.music.durationSeconds 182 */ }
```

- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — `MusicController`의 `@Validated`가 `@Positive`를 타는지 확인. `DiaryTestSupport.insertDiary`에 `Long musicId` 오버로드 추가.
- [ ] **Step 4: 스크립트 작성** — `scripts/backfill-music-duration.sh`: `YOUTUBE_API_KEY` 필수, 표준 입력으로 `video_id` 목록(한 줄 하나)을 받아 50개씩 `https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id=...` 호출, `jq`로 `PT3M2S` → 182 변환, `UPDATE music SET duration_seconds = 182 WHERE video_id = 'abc';` 출력. 사용법 주석에 `mysql -N -e "select video_id from music" | ./scripts/backfill-music-duration.sh > update.sql`.
- [ ] **Step 5: DDL 추가** — `ALTER TABLE music ADD COLUMN duration_seconds INT NULL;`
- [ ] **Step 6: 통과 확인 + 전체 테스트**
- [ ] **Step 7: Commit** — `feat. 음악 재생 시간(durationSeconds) 저장·응답, 백필 스크립트`

---

### Task 7: 통계와 일일 제한 (스펙 2, 6)

**Files:**
- Create: `service/DiaryStatsService.java`
- Create: `dto/response/DiaryStatsResponseDto.java`
- Modify: `repository/DiaryRepository.java`, `service/DiaryService.java`, `controller/UserController.java`, `application.properties`
- Test: `controller/DiaryStatsIntegrationTest.java` (extends `DiaryTestSupport`)

**Interfaces:**
- Produces: `DiaryRepository.countByUserIdAndIsValidTrue(String)`, `countByUserIdAndIsValidTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String, LocalDateTime, LocalDateTime)`, `@Query(nativeQuery) List<java.sql.Date> findDistinctDiaryDates(String userId)` — `SELECT DISTINCT DATE(created_at) FROM diary WHERE user_id = :userId AND is_valid = 1 ORDER BY 1 DESC`
- Produces: `DiaryStatsService(DiaryRepository, Clock, @Value("${diary.daily-limit:3}") int dailyLimit)`: `getStats(String memberId) : DiaryStatsResponseDto`, `assertUnderDailyLimit(String memberId)` (초과 시 `BusinessException(DIARY_DAILY_LIMIT_EXCEEDED)`), `static int streakDays(List<LocalDate> datesDesc, LocalDate today)`
- Produces: `DiaryStatsResponseDto { long totalCount; long monthCount; int streakDays; long todayCount; int dailyLimit; }`
- Produces: `GET /users/me/stats`
- `DiaryService.generate`와 `doConfirm` 첫 줄(OpenAI 호출 전)에서 `diaryStatsService.assertUnderDailyLimit(userId)`

- [ ] **Step 1: 테스트 작성** — 날짜는 `LocalDate.now(KstTime.ZONE)` 기준 상대값으로 만든다.

```java
@Test void statsCountTotalMonthToday() { /* 지난달 1개, 이번달 어제 1개, 오늘 1개 → total 3, month 2, today 1 */ }
@Test void todayCountRespectsKstMidnight() { /* 어제 23:59:59, 오늘 00:00:00 → today 1 */ }
@Test void streakCountsConsecutiveDays() { /* 오늘, 어제, 3일 전 → streak 2 */ }
@Test void streakStartsFromYesterdayWhenNoDiaryToday() { /* 어제, 그제 → streak 2; 그제만 → 0 */ }
@Test void statsIncludeDailyLimit() { /* $.dailyLimit == 3 */ }
@Test void fourthDiaryOfTheDayIs429() { /* 오늘 3개 삽입 후 POST /diaries/free → 429, $.code DIARY_DAILY_LIMIT_EXCEEDED; POST /diaries/qna 도 429; openAiService.call 호출 없음 verify */ }
@Test void deletedDiaryDoesNotCountTowardLimit() { /* 오늘 3개 중 1개 DELETE 후 POST /diaries/free → 201 */ }
```

- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — `streakDays`: `today`가 집합에 있으면 `today`부터, 아니면 `today-1`부터 하루씩 빼며 연속 카운트. `application.properties`에 `diary.daily-limit=3`.
- [ ] **Step 4: 통과 확인 + 전체 테스트** — H2에서 `DATE()`와 `java.sql.Date` 매핑이 안 되면 `List<Object>`로 받아 `LocalDate` 변환.
- [ ] **Step 5: Commit** — `feat. 마이페이지 통계(GET /users/me/stats), 하루 3개 작성 제한 429`

---

### Task 8: provider와 알림 토글 (스펙 3, 4)

**Files:**
- Modify: `entity/Member.java` (`@Column(name="notification_enabled", nullable=false, columnDefinition="TINYINT") @ColumnDefault("0") Boolean notificationEnabled`, `create`에서 false, `changeNotificationEnabled(boolean)`)
- Create: `dto/request/NotificationUpdateRequestDto.java` (`@NotNull Boolean enabled`)
- Create: `dto/response/NotificationResponseDto.java` (`boolean notificationEnabled`)
- Modify: `dto/response/UserMeResponseDto.java` (`String provider`, `boolean notificationEnabled`; `from(Member, SocialProvider)`)
- Modify: `service/UserService.java` (`getMe` → `UserMeResponseDto`, `SocialAccountRepository` 주입; `changeNotificationEnabled(String memberId, boolean)`)
- Modify: `controller/UserController.java` (`PUT /users/me/notification`)
- Modify: `repository/MemberRepository.java` (`findByIsValidTrueAndNotificationEnabledTrue()`)
- Modify: `scheduler/DiaryRemindScheduler.java`, `scheduler/DiarySympathyScheduler.java` (`MemberRepository` 주입, 발송 전 `findByMemberIdAndIsValidTrue(...).map(Member::getNotificationEnabled).orElse(false)`)
- Modify: `docs/db/2026-10-28-app-redesign-api.sql`
- Test: `controller/UserIntegrationTest.java`(기존 `newMemberHasNoNickname`에 provider·notificationEnabled 단언 추가), `controller/NotificationIntegrationTest.java`, `scheduler/DiaryRemindSchedulerTest.java`, `scheduler/DiarySympathySchedulerTest.java`(신규)

- [ ] **Step 1: 테스트 작성**

```java
// NotificationIntegrationTest
@Test void defaultIsOffAndProviderIsGoogle() { /* GET /users/me → $.notificationEnabled false, $.provider "GOOGLE" */ }
@Test void toggleOnThenOff() { /* PUT {"enabled":true} → 200 $.notificationEnabled true; GET /users/me true; PUT false → false */ }
@Test void enabledIsRequired() { /* PUT {} → 400 VALIDATION_FAILED */ }
// DiaryRemindSchedulerTest: 기존 when(memberRepository.findByIsValidTrue()) 를 findByIsValidTrueAndNotificationEnabledTrue() 로 교체하고
@Test void remindSkipsMembersWithNotificationOff() { /* 레포가 켜진 회원만 돌려주는지: findByIsValidTrue 는 호출되지 않음 verify */ }
// DiarySympathySchedulerTest
@Test void sympathySavesMessageButSkipsPushWhenOff() { /* feedbackMsg 갱신됨, fcmService.feedbackNotification never */ }
@Test void sympathyPushesWhenOn() { /* feedbackNotification 1회 */ }
```

- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — `Member`에 테스트용 상태 변경이 필요하면 `changeNotificationEnabled`를 쓴다(setter 추가 금지).
- [ ] **Step 4: DDL 추가** — `ALTER TABLE member ADD COLUMN notification_enabled TINYINT NOT NULL DEFAULT 0;`
- [ ] **Step 5: 통과 확인 + 전체 테스트**
- [ ] **Step 6: Commit** — `feat. GET /users/me 에 provider·notificationEnabled, PUT /users/me/notification, 알림 꺼진 회원 푸시 제외`

---

### Task 9: 최소 버전 (스펙 17)

**Files:**
- Create: `entity/AppVersion.java` (`@Id @Column(name="platform", length=10) String platform; min_version(20, NOT NULL); latest_version(20); store_url(500); updated_at(NOT NULL)`; `static create(platform, min, latest, storeUrl, now)`, `update(min, latest, storeUrl, now)`)
- Create: `repository/AppVersionRepository.java`
- Create: `service/AppVersionService.java`
- Create: `dto/request/AppVersionUpdateRequestDto.java` (`@NotBlank @Pattern(VERSION) minVersion`, `@Pattern(VERSION) latestVersion`, `@Size(max=500) storeUrl`)
- Create: `dto/response/AppVersionResponseDto.java` (`minVersion, latestVersion, storeUrl`), `dto/response/MinVersionResponseDto.java` (`AppVersionResponseDto ios; AppVersionResponseDto android;`)
- Create: `controller/AppVersionController.java` (`GET /app/min-version`), `controller/AdminAppVersionController.java` (`PUT /admin/app/versions/{platform}`)
- Modify: `dto/request/RequestPatterns.java` (`VERSION = "^\\d+\\.\\d+\\.\\d+$"`, `VERSION_MESSAGE`), `config/SecurityConfig.java` (publicPaths에 `matcher(HttpMethod.GET, "/app/min-version")`)
- Modify: `docs/db/2026-10-28-app-redesign-api.sql`
- Test: `controller/AppVersionIntegrationTest.java`

**Interfaces:**
- Produces: `AppVersionService.getMinVersions() : MinVersionResponseDto`, `upsert(String platform, AppVersionUpdateRequestDto) : AppVersionResponseDto` — `platform`이 `IOS`/`AND`가 아니면 `IllegalArgumentException`
- 플랫폼 키: 엔티티 `IOS` → 응답 `ios`, `AND` → `android`

- [ ] **Step 1: 테스트 작성** — 관리자 토큰 헬퍼는 `AdminAuthIntegrationTest`의 signup→approve→login 흐름을 `IntegrationTestSupport.loginAsAdmin()`으로 옮겨 공유한다(기존 테스트는 손대지 않음).

```java
@Test void emptyTableGivesNullsWithoutToken() { /* GET /app/min-version (토큰 없음) → 200, $.ios null, $.android null */ }
@Test void adminUpsertThenPublicRead() { /* PUT /admin/app/versions/IOS {"minVersion":"1.2.0","latestVersion":"1.3.0","storeUrl":"https://apps.apple.com/x"} → 200; PUT 다시 min 1.2.1 → 200; GET → $.ios.minVersion "1.2.1", $.android null */ }
@Test void invalidVersionOrPlatformIs400() { /* min "1.2" → 400 VALIDATION_FAILED; platform WEB → 400 BAD_REQUEST */ }
@Test void memberTokenCannotUpsert() { /* 앱 토큰으로 PUT → 401 (관리자 체인은 앱 키 토큰을 거부) */ }
@Test void minVersionIgnoresBearerToken() { /* Authorization: Bearer garbage 를 붙여도 GET → 200 */ }
```

- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현**
- [ ] **Step 4: DDL 추가**

```sql
CREATE TABLE app_version (
  platform       VARCHAR(10)  NOT NULL,
  min_version    VARCHAR(20)  NOT NULL,
  latest_version VARCHAR(20)  NULL,
  store_url      VARCHAR(500) NULL,
  updated_at     DATETIME     NOT NULL,
  PRIMARY KEY (platform)
);
```

- [ ] **Step 5: 통과 확인 + 전체 테스트**
- [ ] **Step 6: Commit** — `feat. 앱 최소 버전 조회(GET /app/min-version)와 관리자 등록 API`

---

### Task 10: 스웨거 문서와 마무리 (스펙 5, 11, 13)

**Files:**
- Modify: `config/OpenApiConfig.java` (설명에 에러 코드 표: 선행 스펙 9.2 + `DIARY_NOT_FOUND`, `DIARY_DAILY_LIMIT_EXCEEDED`; `TOKEN_EXPIRED`와 `TOKEN_INVALID`의 차이와 앱 권장 동작; `createdAt` 오프셋; 정렬 값)
- Modify: `controller/UserController.java` (`@Operation(summary, description)`에 닉네임 정규식 `^[가-힣A-Za-z0-9]{2,10}$`과 중복 허용 명시), `controller/DiaryController.java` (`/diaries/qna`에 "답이 없는 문항은 빼고 보낸다, 전부 건너뛰면 호출하지 않는다", `/diaries`에 sort·페이지 메타, `/diaries/calendar`에 sort), `controller/AppVersionController.java` (배포 순서 설명)
- Modify: `docs/db/2026-10-28-app-redesign-api.sql` (최종 점검: 스펙 4장과 일치)

- [ ] **Step 1: `@Operation` 어노테이션과 `OpenApiConfig` 설명 작성** — `io.swagger.v3.oas.annotations.Operation`(springdoc 포함)
- [ ] **Step 2: 로컬 기동으로 스웨거 확인이 가능하면 `./run-local.sh` 후 `http://localhost:8080/v3/api-docs`에서 `DiaryPageResponseDto`, `MinVersionResponseDto`, 설명 표가 보이는지 확인. 불가능하면(DB·Redis 없음) 건너뛰고 보고에 적는다.**
- [ ] **Step 3: 전체 테스트** — `./gradlew test` → PASS
- [ ] **Step 4: Commit** — `docs. 스웨거에 에러 코드 표·닉네임 규칙·정렬 값 설명 추가`

---

## 자체 검토

- 스펙 3장 항목 ↔ 태스크: 16→1, 13→2·10, 1→3, 7·8→4, 9→5, 10→6, 2·6→7, 3·4→8, 17→9, 5·11→10. 15·12·14는 변경 없음.
- 스펙 4장 DDL ↔ 태스크 3·5·6·8·9에서 누적, 10에서 점검.
- Review Focus 6개 ↔ 각 태스크 테스트 이름으로 연결됨.
