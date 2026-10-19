package com.momeokji.aiDiarybackend.service.auth.social;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.entity.SocialProvider;
import com.momeokji.aiDiarybackend.security.RefreshTokens;

/**
 * Apple identity token 검증(스펙 5.3). 서명·iss·aud·exp 에 더해 nonce 를 확인한다:
 * 앱이 만든 원본 nonce 의 SHA-256(hex) 이 토큰의 {@code nonce} claim 과 같아야 한다.
 */
@Component
public class AppleIdTokenVerifier {

	private final JwtDecoder decoder;

	public AppleIdTokenVerifier(@Qualifier("appleIdTokenDecoder") JwtDecoder decoder) {
		this.decoder = decoder;
	}

	public SocialIdentity verify(String identityToken, String rawNonce) {
		Jwt jwt;
		try {
			jwt = decoder.decode(identityToken);
		} catch (JwtException e) {
			throw SocialTokenErrors.translate(e);
		}

		String nonceClaim = jwt.getClaimAsString("nonce");
		if (nonceClaim == null || rawNonce == null || !constantTimeEquals(nonceClaim, RefreshTokens.sha256Hex(rawNonce))) {
			throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID, "nonce가 일치하지 않습니다.");
		}
		return new SocialIdentity(SocialProvider.APPLE, SocialTokenErrors.requireSubject(jwt), jwt.getClaimAsString("email"));
	}

	private static boolean constantTimeEquals(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}
}
