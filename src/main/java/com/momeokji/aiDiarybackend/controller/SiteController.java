package com.momeokji.aiDiarybackend.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * 공개 사이트 페이지(토큰 불필요). Google OAuth 브랜드 인증이 직접 열어 보는 페이지다.
 *
 * <p>구글 요건(support.google.com/cloud/answer/13807376, 13806988):
 * <ul>
 *   <li>홈페이지는 로그인 없이 열리고, 앱을 식별하고, 기능과 구글 사용자 데이터를 요청하는 이유를 설명하고,
 *       개인정보처리방침으로 가는 링크가 있어야 한다. 다른 주소로 리다이렉트하면 안 된다.</li>
 *   <li>개인정보처리방침은 홈페이지와 같은 도메인의 전용 페이지 본문에 HTML 로 있어야 한다.
 *       구글은 PDF 를 처리하지 못한다. 그래서 S3 의 PDF 로 보내지 않고 여기서 직접 낸다.</li>
 * </ul>
 *
 * <p>문서는 {@code classpath:site/*.html} 에 있고 기동할 때 한 번 읽는다. 없으면 기동에 실패한다.
 * 방침이나 약관 문구를 바꾸려면 그 파일을 고치고 배포한다.
 */
@Hidden
@RestController
public class SiteController {

	private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

	private final String home = load("home");
	private final String privacy = load("privacy");
	private final String terms = load("terms");

	@GetMapping("/")
	public ResponseEntity<String> home() {
		return html(home);
	}

	@GetMapping("/privacy")
	public ResponseEntity<String> privacy() {
		return html(privacy);
	}

	@GetMapping("/terms")
	public ResponseEntity<String> terms() {
		return html(terms);
	}

	private static ResponseEntity<String> html(String body) {
		return ResponseEntity.ok().contentType(HTML_UTF8).body(body);
	}

	private static String load(String name) {
		String location = "site/" + name + ".html";
		try {
			return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("공개 페이지 파일을 읽을 수 없습니다: classpath:" + location, e);
		}
	}
}
