package com.momeokji.aiDiarybackend.controller;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.config.SiteProperties;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

/**
 * 공개 사이트 경로(토큰 불필요). Google OAuth 동의 화면이 홈페이지·개인정보처리방침·이용약관 링크를
 * 직접 열어 보므로 전부 200 또는 302 여야 한다. 문서는 S3 의 PDF 로 보낸다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class SiteController {

	static final String HOME_HTML = """
		<!doctype html>
		<html lang="ko">
		<head>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<title>조각케이크</title>
		</head>
		<body>
		<h1>조각케이크</h1>
		<p>하루를 한 조각씩 기록하는 AI 일기 앱입니다.</p>
		<p><a href="/privacy">개인정보처리방침</a> · <a href="/terms">서비스 이용약관</a></p>
		</body>
		</html>
		""";

	private final SiteProperties siteProperties;

	@GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
	public String home() {
		return HOME_HTML;
	}

	@GetMapping("/privacy")
	public ResponseEntity<Void> privacy() {
		return redirect(siteProperties.privacyUrl());
	}

	@GetMapping("/terms")
	public ResponseEntity<Void> terms() {
		return redirect(siteProperties.termsUrl());
	}

	private static ResponseEntity<Void> redirect(String url) {
		return ResponseEntity.status(302).location(URI.create(url)).build();
	}
}
