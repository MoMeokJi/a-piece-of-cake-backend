package com.momeokji.aiDiarybackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공개 사이트 링크. Google OAuth 동의 화면에 등록하는 개인정보처리방침·이용약관 문서 주소다.
 *
 * @param privacyUrl {@code GET /privacy} 가 302 로 보내는 주소
 * @param termsUrl {@code GET /terms} 가 302 로 보내는 주소
 */
@ConfigurationProperties("site")
public record SiteProperties(String privacyUrl, String termsUrl) {
}
