package com.momeokji.aiDiarybackend.service.auth;

/**
 * 로그인 또는 refresh 로 발급된 토큰 한 쌍. 만료는 초 단위다.
 * {@code nickname} 은 응답에 함께 실을 회원 닉네임으로, 아직 정하지 않았으면 null 이다.
 */
public record IssuedTokens(
	String accessToken,
	long accessTokenExpiresIn,
	String refreshToken,
	long refreshTokenExpiresIn,
	String nickname
) {
}
