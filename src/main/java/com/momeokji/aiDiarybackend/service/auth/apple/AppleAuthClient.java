package com.momeokji.aiDiarybackend.service.auth.apple;

import java.util.Optional;

/**
 * 애플 인증 서버 호출. 둘 다 예외를 밖으로 던지지 않는다 — 실패해도 로그인/탈퇴는 계속 진행한다(스펙 5.3).
 */
public interface AppleAuthClient {

	/** authorization code 를 애플 refresh 토큰으로 교환한다. 실패하면 empty + WARN 로그. */
	Optional<String> exchangeAuthorizationCode(String authorizationCode);

	/** 저장해 둔 애플 refresh 토큰을 revoke 한다. 실패하면 false + ERROR 로그. */
	boolean revoke(String appleRefreshToken);
}
