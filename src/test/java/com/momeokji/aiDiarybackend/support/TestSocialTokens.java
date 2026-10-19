package com.momeokji.aiDiarybackend.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.momeokji.aiDiarybackend.config.SocialTokenDecoderConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 테스트용 소셜 ID 토큰. 외부 JWKS 대신 여기서 만든 RSA 키로 서명하고, 같은 공개키로 만든 디코더로 검증한다.
 * 검증 규칙(iss/aud/exp)은 운영 코드의 정적 validator 를 그대로 쓴다.
 */
public final class TestSocialTokens {

	public static final String GOOGLE_ISSUER = "https://accounts.google.com";
	public static final String APPLE_ISSUER = "https://appleid.apple.com";

	private static final KeyPair KEY_PAIR = generate();
	private static final KeyPair OTHER_KEY_PAIR = generate();

	private TestSocialTokens() {
	}

	public static JwtDecoder googleDecoder(List<String> clientIds) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
		decoder.setJwtValidator(SocialTokenDecoderConfig.googleValidator(clientIds));
		return decoder;
	}

	public static JwtDecoder appleDecoder(String clientId) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
		decoder.setJwtValidator(SocialTokenDecoderConfig.appleValidator(clientId));
		return decoder;
	}

	public static String google(String sub, String aud, String email) {
		return sign(KEY_PAIR, claims(GOOGLE_ISSUER, sub, aud, Instant.now().plusSeconds(600))
			.claim("email", email).build());
	}

	public static String googleWithIssuer(String issuer, String sub, String aud) {
		return sign(KEY_PAIR, claims(issuer, sub, aud, Instant.now().plusSeconds(600)).build());
	}

	public static String expiredGoogle(String sub, String aud) {
		return sign(KEY_PAIR, claims(GOOGLE_ISSUER, sub, aud, Instant.now().minusSeconds(600)).build());
	}

	public static String googleSignedByOtherKey(String sub, String aud) {
		return sign(OTHER_KEY_PAIR, claims(GOOGLE_ISSUER, sub, aud, Instant.now().plusSeconds(600)).build());
	}

	/** {@code sub} 가 없는 구글 토큰. 서명·iss·aud·exp 는 모두 정상이다. */
	public static String googleWithoutSub(String aud) {
		return sign(KEY_PAIR, claims(GOOGLE_ISSUER, null, aud, Instant.now().plusSeconds(600)).build());
	}

	/** {@code sub} 가 없는 애플 토큰. 서명·iss·aud·exp·nonce 는 모두 정상이다. */
	public static String appleWithoutSub(String aud, String nonceHash) {
		return sign(KEY_PAIR, claims(APPLE_ISSUER, null, aud, Instant.now().plusSeconds(600)).claim("nonce", nonceHash).build());
	}

	public static String apple(String sub, String aud, String nonceHash, String email) {
		JWTClaimsSet.Builder b = claims(APPLE_ISSUER, sub, aud, Instant.now().plusSeconds(600));
		if (nonceHash != null) {
			b.claim("nonce", nonceHash);
		}
		if (email != null) {
			b.claim("email", email);
		}
		return sign(KEY_PAIR, b.build());
	}

	public static String appleWithIssuer(String issuer, String sub, String aud, String nonceHash) {
		return sign(KEY_PAIR, claims(issuer, sub, aud, Instant.now().plusSeconds(600)).claim("nonce", nonceHash).build());
	}

	private static JWTClaimsSet.Builder claims(String issuer, String sub, String aud, Instant exp) {
		return new JWTClaimsSet.Builder()
			.issuer(issuer)
			.subject(sub)
			.audience(aud)
			.issueTime(Date.from(Instant.now().minusSeconds(5)))
			.expirationTime(Date.from(exp));
	}

	private static String sign(KeyPair keyPair, JWTClaimsSet claims) {
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(), claims);
			jwt.sign(new RSASSASigner(keyPair.getPrivate()));
			return jwt.serialize();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static KeyPair generate() {
		try {
			KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
			gen.initialize(2048);
			return gen.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
