package com.momeokji.aiDiarybackend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 자체 발급 JWT 설정(스펙 10장). 앱용과 관리자용 서명 키를 분리한다.
 */
@ConfigurationProperties("jwt")
public record JwtProperties(
	String issuer,
	MemberToken member,
	AdminToken admin
) {
	public record MemberToken(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

	public record AdminToken(String secret, Duration accessTokenTtl) {}
}
