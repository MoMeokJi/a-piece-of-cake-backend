package com.momeokji.aiDiarybackend.service.auth;

/** 로그인 또는 refresh 로 발급된 토큰 한 쌍. 만료는 초 단위다. */
public record IssuedTokens(
	String accessToken,
	long accessTokenExpiresIn,
	String refreshToken,
	long refreshTokenExpiresIn
) {
}
