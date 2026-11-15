package com.momeokji.aiDiarybackend.service.auth.social;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import com.momeokji.aiDiarybackend.entity.SocialProvider;

/** Google ID 토큰 검증(스펙 5.2). */
@Component
public class GoogleIdTokenVerifier {

	private final JwtDecoder decoder;

	public GoogleIdTokenVerifier(@Qualifier("googleIdTokenDecoder") JwtDecoder decoder) {
		this.decoder = decoder;
	}

	public SocialIdentity verify(String idToken) {
		try {
			Jwt jwt = decoder.decode(idToken);
			return new SocialIdentity(SocialProvider.GOOGLE, SocialTokenErrors.requireSubject(jwt), jwt.getClaimAsString("email"));
		} catch (JwtException e) {
			throw SocialTokenErrors.translate(e);
		}
	}
}
