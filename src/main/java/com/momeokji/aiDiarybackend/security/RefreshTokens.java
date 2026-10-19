package com.momeokji.aiDiarybackend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 앱 refresh 토큰(스펙 4.2). 32바이트 난수를 base64url 로 내보내고, DB 에는 SHA-256(hex) 만 저장한다.
 * 엔트로피가 충분해 bcrypt 같은 느린 해시는 필요 없다.
 */
public final class RefreshTokens {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private RefreshTokens() {
	}

	public static String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
		}
	}
}
