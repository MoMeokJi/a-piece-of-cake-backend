package com.momeokji.aiDiarybackend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

/**
 * 공개 사이트 경로. Google OAuth 동의 화면에 등록하는 홈페이지·개인정보처리방침·이용약관 링크라
 * 토큰 없이 200 또는 302 여야 한다.
 */
class SitePagesIntegrationTest extends IntegrationTestSupport {

	static final String PRIVACY_PDF =
		"https://piece-of-cake-image.s3.ap-northeast-2.amazonaws.com/%EC%A1%B0%EA%B0%81%EC%BC%80%EC%9D%B4%ED%81%AC_%EA%B0%9C%EC%9D%B8%EC%A0%95%EB%B3%B4_%EC%B2%98%EB%A6%AC%EB%B0%A9%EC%B9%A8.pdf";
	static final String TERMS_PDF =
		"https://piece-of-cake-image.s3.ap-northeast-2.amazonaws.com/%EC%A1%B0%EA%B0%81%EC%BC%80%EC%9D%B4%ED%81%AC_%EC%84%9C%EB%B9%84%EC%8A%A4_%EC%9D%B4%EC%9A%A9%EC%95%BD%EA%B4%80.pdf";

	@Test
	void homeIsPublicHtmlWithAppName() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
			.andExpect(content().string(containsString("조각케이크")));
	}

	@Test
	void privacyRedirectsToPdfWithoutToken() throws Exception {
		mockMvc.perform(get("/privacy"))
			.andExpect(status().isFound())
			.andExpect(header().string("Location", PRIVACY_PDF));
	}

	@Test
	void termsRedirectsToPdfWithoutToken() throws Exception {
		mockMvc.perform(get("/terms"))
			.andExpect(status().isFound())
			.andExpect(header().string("Location", TERMS_PDF));
	}
}
