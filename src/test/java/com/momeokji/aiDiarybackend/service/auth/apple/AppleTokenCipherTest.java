package com.momeokji.aiDiarybackend.service.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.momeokji.aiDiarybackend.config.OAuthProperties;

class AppleTokenCipherTest {

	static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

	final AppleTokenCipher cipher = new AppleTokenCipher(props(KEY));

	@Test
	void roundTrip() {
		assertThat(cipher.decrypt(cipher.encrypt("apple-rt"))).isEqualTo("apple-rt");
	}

	@Test
	void encryptionIsRandomized() {
		assertThat(cipher.encrypt("apple-rt")).isNotEqualTo(cipher.encrypt("apple-rt"));
	}

	@Test
	void ciphertextDoesNotContainPlaintext() {
		assertThat(cipher.encrypt("apple-rt")).doesNotContain("apple-rt");
	}

	@Test
	void tamperedCiphertextFails() {
		String encoded = cipher.encrypt("apple-rt");
		byte[] bytes = Base64.getDecoder().decode(encoded);
		bytes[bytes.length - 1] ^= 0x01;
		String tampered = Base64.getEncoder().encodeToString(bytes);

		assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void wrongKeyLengthFailsFast() {
		String shortKey = Base64.getEncoder().encodeToString("short".getBytes());

		assertThatThrownBy(() -> new AppleTokenCipher(props(shortKey))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new AppleTokenCipher(props(null))).isInstanceOf(IllegalStateException.class);
	}

	private static OAuthProperties props(String key) {
		return new OAuthProperties(new OAuthProperties.Google(null),
			new OAuthProperties.Apple("com.app", "TEAM", "KID", null, key, null));
	}
}
