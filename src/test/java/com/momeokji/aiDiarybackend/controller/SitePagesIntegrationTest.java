package com.momeokji.aiDiarybackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

/**
 * 공개 사이트 경로. Google OAuth 브랜드 인증이 직접 열어 보는 페이지라 구글 요건을 그대로 검증한다.
 *
 * <ul>
 *   <li>홈페이지: 로그인 없이 열리고, 앱 이름·기능·구글 데이터를 요청하는 이유가 있고, 개인정보처리방침 링크가 있다</li>
 *   <li>개인정보처리방침: 우리 도메인에서 HTML 본문으로 직접 낸다. PDF 나 다른 도메인으로 리다이렉트하면 안 된다</li>
 *   <li>개인정보처리방침은 구글 사용자 데이터를 무엇을 받고 어떻게 쓰는지 밝힌다</li>
 * </ul>
 */
class SitePagesIntegrationTest extends IntegrationTestSupport {

	@ParameterizedTest
	@ValueSource(strings = {"/", "/privacy", "/terms", "/ai-consent"})
	void pagesAreServedAsHtmlWithoutTokenOrRedirect(String path) throws Exception {
		mockMvc.perform(get(path))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
			.andExpect(content().encoding("UTF-8"))
			.andExpect(header().doesNotExist("Location"));
	}

	@Test
	void homeIdentifiesTheAppExplainsItsPurposeAndLinksToThePolicies() throws Exception {
		String html = body("/");

		assertThat(html).contains("<title>조각케이크</title>");
		assertThat(html).contains("<h1>조각케이크</h1>");
		// 기능 설명
		assertThat(html).contains("문답 일기", "자유 일기", "공감 메시지");
		// 구글 사용자 데이터를 왜 요청하는지
		assertThat(html).contains("Google 계정", "이메일 주소");
		// 동의 화면에 등록한 주소와 같은 경로의 링크
		assertThat(html).contains("href=\"/privacy\"", "href=\"/terms\"");
	}

	@Test
	void privacyPolicyDisclosesWhatGoogleDataIsUsedAndHow() throws Exception {
		String html = body("/privacy");

		assertThat(html).contains("<h1>조각케이크 개인정보 처리방침</h1>");
		assertThat(html).contains("Google", "Apple", "계정 고유 식별자", "이메일 주소");
		assertThat(html).contains("OpenAI", "회원 탈퇴");
		assertThat(html).contains("team.momeokji@gmail.com");
	}

	@Test
	void termsDescribeSocialLogin() throws Exception {
		String html = body("/terms");

		assertThat(html).contains("<h1>조각케이크 서비스 이용약관</h1>");
		assertThat(html).contains("Google 계정 또는 Apple 계정");
	}

	/**
	 * 앱은 구글·애플 계정의 이름을 닉네임 입력란의 초깃값으로 쓴다. 쓰는 데이터를 빠짐없이 밝혀야 한다
	 * (구글 브랜드 인증이 고지와 실제 사용의 일치를 본다). 앱이 이름을 쓰지 않게 되면 이 문구도 함께 뺀다.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"/", "/privacy"})
	void pagesDiscloseThatTheAccountNameIsUsedAsTheNicknameDefault(String path) throws Exception {
		String html = body(path);

		assertThat(html).contains("닉네임 입력란");
		assertThat(html).contains("이름");
		assertThat(html).doesNotContain("이메일 주소만 이용");
		assertThat(html).doesNotContain("(이름, 프로필 사진 등)는 저장하지 않습니다");
	}

	/**
	 * 영구 삭제 기한은 삭제 스케줄러의 유예 3일 + 실행 주기 1일이다.
	 * 스케줄러 쪽은 {@code DiaryDeleteSchedulerIntegrationTest} 가 지킨다. 둘 중 하나를 바꾸면 다른 쪽도 바꿔야 한다.
	 */
	@Test
	void privacyPolicyPromisesPermanentDeletionWithinFourDays() throws Exception {
		String html = body("/privacy");

		assertThat(html).contains("4일 이내에 서버에서 영구 삭제");
		assertThat(html).doesNotContain("6일 이내");
	}

	/** 옛 익명(기기 기반) 앱 시절 문구가 남아 있으면 지금 서비스와 다른 내용을 고지하게 된다. */
	@ParameterizedTest
	@ValueSource(strings = {"/privacy", "/terms"})
	void policiesNoLongerDescribeTheAnonymousApp(String path) throws Exception {
		String html = body(path);

		assertThat(html).doesNotContain("별도의 회원가입");
		assertThat(html).doesNotContain("6개월");
		assertThat(html).doesNotContain("재설치 시 이전 데이터를 복구할 수 없");
	}

	/**
	 * 국외 이전 동의문과 방침 5.1 은 같은 사실을 말해야 한다(받는 자, 연락처, 보유 기간, 학습 미사용, 보내지 않는 항목).
	 * 보유 기간은 {@code OpenAiServiceTest} 가 지키는 {@code store=false} 를 전제로 한다.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"/privacy", "/ai-consent"})
	void overseasTransferIsDescribedTheSameWayInBothDocuments(String path) throws Exception {
		String html = body(path);

		assertThat(html).contains("OpenAI OpCo, LLC", "dsar@openai.com", "미국");
		assertThat(html).contains("30일 이내 삭제", "AI 모델 학습에 사용되지 않습니다");
		assertThat(html).contains("저장 후 몇 분 안에 시식평을 작성할 때 암호화된 네트워크를 통해 전송");
		// 문답 일기 생성 때 지난 일기도 함께 간다(DiaryService.generate 의 reference_sets). 동의문이 이를 빼면 동의 범위가 실제보다 좁아진다.
		assertThat(html).contains("최근에 작성한 문답 일기(최대 5건)의 문답과 일기 내용을 함께 전송합니다");
		assertThat(html).contains("이 경우 서비스를 이용할 수 없습니다");
	}

	@Test
	void aiConsentListsWhatIsAndIsNotTransferred() throws Exception {
		String html = body("/ai-consent");

		assertThat(html).contains("<h1>AI 일기 생성을 위한 개인정보 국외 이전 동의</h1>");
		assertThat(html).contains("문답 질문과 답변 내용, 작성한 일기 내용", "계정 정보와 사진은 전송하지 않습니다");
		assertThat(html).contains("href=\"/privacy\"");
	}

	/** 앱이 실제로 쓰는 외부 서비스와 계정 삭제 요청 경로는 방침에 있어야 한다(스토어 심사 요건). */
	@Test
	void privacyPolicyListsFirebaseAnalyticsAndTheAccountDeletionPage() throws Exception {
		String html = body("/privacy");

		assertThat(html).contains("Firebase Analytics·Crashlytics");
		assertThat(html).contains("https://maddening-cello-e93.notion.site/4252fe56bc3380a99ce5d5d0be823857");
		assertThat(html).contains("href=\"/ai-consent\"");
	}

	/** 가입할 때 저장하는 약관 동의 기록도 수집 항목이고, 탈퇴하면 지운다({@code TermsAgreementIntegrationTest}). */
	@Test
	void privacyPolicyDisclosesConsentRecords() throws Exception {
		String html = body("/privacy");

		assertThat(html).contains("약관 동의 기록: 가입할 때 동의한 약관과 동의 일시");
		assertThat(html).contains("닉네임, 약관 동의 기록은 즉시 삭제합니다");
	}

	private String body(String path) throws Exception {
		return mockMvc.perform(get(path))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
	}
}
