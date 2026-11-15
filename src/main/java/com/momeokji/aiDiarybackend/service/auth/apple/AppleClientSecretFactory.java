package com.momeokji.aiDiarybackend.service.auth.apple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.momeokji.aiDiarybackend.config.OAuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 애플 토큰 API 용 client_secret(ES256 JWT) 생성(스펙 5.3).
 * {@code iss}=Team ID, {@code sub}=client ID, {@code aud}=https://appleid.apple.com, 헤더 {@code kid}=Key ID.
 * 유효기간은 5분으로 짧게 두고 호출마다 새로 만든다. .p8 은 첫 호출 때 읽어 캐시한다.
 *
 * <p>설정이나 파일이 없어도 앱 기동은 막지 않는다. 호출 시점에 {@link IllegalStateException} 을 던진다.
 */
@Component
public class AppleClientSecretFactory {

	public static final String APPLE_AUDIENCE = "https://appleid.apple.com";
	private static final Duration TTL = Duration.ofMinutes(5);

	private final OAuthProperties.Apple apple;
	private final Clock clock;
	private volatile ECPrivateKey privateKey;

	public AppleClientSecretFactory(OAuthProperties props, Clock clock) {
		this.apple = props.apple();
		this.clock = clock;
	}

	public String create() {
		if (apple == null || isBlank(apple.teamId()) || isBlank(apple.keyId()) || isBlank(apple.clientId())) {
			throw new IllegalStateException("oauth.apple.team-id / key-id / client-id 가 설정되지 않았습니다.");
		}
		Instant now = clock.instant();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
			.issuer(apple.teamId())
			.subject(apple.clientId())
			.audience(APPLE_AUDIENCE)
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plus(TTL)))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(apple.keyId()).build(), claims);
		try {
			jwt.sign(new ECDSASigner(privateKey()));
		} catch (JOSEException e) {
			throw new IllegalStateException("애플 client_secret 서명 실패", e);
		}
		return jwt.serialize();
	}

	private ECPrivateKey privateKey() {
		ECPrivateKey key = privateKey;
		if (key == null) {
			synchronized (this) {
				if (privateKey == null) {
					privateKey = loadPrivateKey();
				}
				key = privateKey;
			}
		}
		return key;
	}

	private ECPrivateKey loadPrivateKey() {
		if (isBlank(apple.privateKeyPath())) {
			throw new IllegalStateException("oauth.apple.private-key-path 가 설정되지 않았습니다.");
		}
		try {
			String pem = Files.readString(Path.of(apple.privateKeyPath()));
			String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
			byte[] der = Base64.getDecoder().decode(body);
			return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
		} catch (IOException | java.security.GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("애플 .p8 키를 읽을 수 없습니다: " + apple.privateKeyPath(), e);
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
