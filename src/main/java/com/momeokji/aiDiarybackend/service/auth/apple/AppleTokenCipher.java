package com.momeokji.aiDiarybackend.service.auth.apple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.momeokji.aiDiarybackend.config.OAuthProperties;

/**
 * 애플 refresh 토큰 저장용 AES-256-GCM 암호화(스펙 7.3). 저장 형식은 {@code base64(iv || ciphertext+tag)}.
 * 키는 base64 32바이트이며, 아니면 기동 시 실패한다.
 */
@Component
public class AppleTokenCipher {

	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final int KEY_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKeySpec key;

	public AppleTokenCipher(OAuthProperties props) {
		String encoded = props.apple() == null ? null : props.apple().tokenEncryptionKey();
		if (encoded == null || encoded.isBlank()) {
			throw new IllegalStateException("oauth.apple.token-encryption-key 가 설정되지 않았습니다. `openssl rand -base64 32` 로 만든 값을 넣으세요.");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(encoded.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("oauth.apple.token-encryption-key 는 base64 여야 합니다.", e);
		}
		if (bytes.length != KEY_BYTES) {
			throw new IllegalStateException("oauth.apple.token-encryption-key 는 32바이트여야 합니다. (현재 " + bytes.length + "바이트)");
		}
		this.key = new SecretKeySpec(bytes, "AES");
	}

	public String encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			RANDOM.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("애플 refresh 토큰 암호화 실패", e);
		}
	}

	public String decrypt(String encoded) {
		try {
			byte[] all = Base64.getDecoder().decode(encoded);
			if (all.length <= IV_BYTES) {
				throw new IllegalStateException("암호문이 너무 짧습니다.");
			}
			ByteBuffer buffer = ByteBuffer.wrap(all);
			byte[] iv = new byte[IV_BYTES];
			buffer.get(iv);
			byte[] encrypted = new byte[buffer.remaining()];
			buffer.get(encrypted);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("애플 refresh 토큰 복호화 실패", e);
		}
	}
}
