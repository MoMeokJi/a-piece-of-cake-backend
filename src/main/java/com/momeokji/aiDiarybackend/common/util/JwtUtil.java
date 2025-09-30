package com.momeokji.aiDiarybackend.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.momeokji.aiDiarybackend.entity.Member;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.issuer}")
	private String issuer;

	@Value("${jwt.expiration.access-token-ms}")
	private long accessExpMs;

	@Value("${jwt.expiration.refresh-token-ms}")
	private long refreshExpMs;

	private SecretKey key() {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String generateAccessToken(Member member){
		return createToken(member, "ACCESS", accessExpMs);
	}

	public String generateRefreshToken(Member member){
		return createToken(member, "REFRESH", refreshExpMs);
	}

	private String createToken(Member member, String typ, long expMs) {
		long now = System.currentTimeMillis();
		return Jwts.builder()
			.issuer(issuer)
			.subject(member.getMemberId()) // sub = memberId
			.claims(Map.of("did", member.getDeviceId(), "typ", typ)) // did = deviceId
			.issuedAt(new Date(now))
			.expiration(new Date(now + expMs))
			.signWith(key(), Jwts.SIG.HS256)
			.compact();
	}

	public Jws<Claims> parse(String token) {
		return Jwts.parser()
			.requireIssuer(issuer)
			.verifyWith(key())                 // SecretKey
			.build()
			.parseSignedClaims(token);         // Jws<Claims> 반환
	}

	public static String resolveBearer(String header) {
		return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
	}
}
