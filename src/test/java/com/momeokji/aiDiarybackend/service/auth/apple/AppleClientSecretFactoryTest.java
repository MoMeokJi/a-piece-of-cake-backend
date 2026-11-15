package com.momeokji.aiDiarybackend.service.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.momeokji.aiDiarybackend.config.OAuthProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;

class AppleClientSecretFactoryTest {

	final Clock clock = Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), ZoneOffset.UTC);

	@Test
	void clientSecretIsEs256WithAppleClaims(@TempDir Path dir) throws Exception {
		KeyPair keyPair = ecKeyPair();
		Path pem = writePkcs8Pem(dir, keyPair);
		AppleClientSecretFactory factory = new AppleClientSecretFactory(props(pem.toString()), clock);

		SignedJWT jwt = SignedJWT.parse(factory.create());

		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
		assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEYID12345");
		assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
		assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("TEAMID1234");
		assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("com.momeokji.cake");
		assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
		assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(clock.instant());
		assertThat(jwt.getJWTClaimsSet().getExpirationTime().getTime() - jwt.getJWTClaimsSet().getIssueTime().getTime())
			.isEqualTo(300_000L);
	}

	@Test
	void missingKeyFileThrowsOnCreate(@TempDir Path dir) {
		AppleClientSecretFactory factory = new AppleClientSecretFactory(props(dir.resolve("missing.p8").toString()), clock);

		assertThatThrownBy(factory::create).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void blankConfigThrowsOnCreateNotOnConstruct() {
		AppleClientSecretFactory factory = new AppleClientSecretFactory(props(""), clock);

		assertThatThrownBy(factory::create).isInstanceOf(IllegalStateException.class);
	}

	private static OAuthProperties props(String keyPath) {
		return new OAuthProperties(new OAuthProperties.Google(null),
			new OAuthProperties.Apple("com.momeokji.cake", "TEAMID1234", "KEYID12345", keyPath, null, null));
	}

	private static KeyPair ecKeyPair() throws Exception {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
		gen.initialize(new ECGenParameterSpec("secp256r1"));
		return gen.generateKeyPair();
	}

	private static Path writePkcs8Pem(Path dir, KeyPair keyPair) throws Exception {
		String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
		Path pem = dir.resolve("AuthKey_KEYID12345.p8");
		Files.writeString(pem, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");
		return pem;
	}
}
