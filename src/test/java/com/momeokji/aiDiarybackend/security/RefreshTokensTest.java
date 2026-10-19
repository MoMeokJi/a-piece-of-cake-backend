package com.momeokji.aiDiarybackend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokensTest {

	@Test
	void generateIsUrlSafeAndUnique() {
		String a = RefreshTokens.generate();
		String b = RefreshTokens.generate();

		assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void sha256HexIsDeterministic64Chars() {
		String h1 = RefreshTokens.sha256Hex("abc");
		String h2 = RefreshTokens.sha256Hex("abc");

		assertThat(h1).hasSize(64).matches("[0-9a-f]+").isEqualTo(h2);
		assertThat(h1).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		assertThat(RefreshTokens.sha256Hex("abd")).isNotEqualTo(h1);
	}
}
