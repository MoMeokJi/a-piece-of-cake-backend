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
	@ValueSource(strings = {"/", "/privacy", "/terms"})
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

	private String body(String path) throws Exception {
		return mockMvc.perform(get(path))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
	}
}
