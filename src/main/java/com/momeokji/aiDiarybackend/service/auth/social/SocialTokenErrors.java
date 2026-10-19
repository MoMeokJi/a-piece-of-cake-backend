package com.momeokji.aiDiarybackend.service.auth.social;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.nimbusds.jose.KeySourceException;

/** 소셜 토큰 디코딩 예외 → 에러 코드 변환(스펙 5.1). */
final class SocialTokenErrors {

	private SocialTokenErrors() {
	}

	/**
	 * {@code sub} 가 없는 토큰은 계정을 식별할 수 없으므로 거부한다. 서명·iss·aud 가 정상이어도 마찬가지다.
	 * (그대로 두면 social_account.provider_user_id NOT NULL 위반으로 500 이 난다.)
	 */
	static String requireSubject(Jwt jwt) {
		String sub = jwt.getSubject();
		if (sub == null || sub.isBlank()) {
			throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID, "토큰에 sub 가 없습니다.");
		}
		return sub;
	}

	static BusinessException translate(JwtException e) {
		if (e instanceof BadJwtException) {
			return new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
		}
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof KeySourceException) {
				return new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
			}
		}
		return new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
	}
}
