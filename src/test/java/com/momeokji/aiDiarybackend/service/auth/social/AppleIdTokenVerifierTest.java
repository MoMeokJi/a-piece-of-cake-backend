package com.momeokji.aiDiarybackend.service.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.entity.SocialProvider;
import com.momeokji.aiDiarybackend.security.RefreshTokens;
import com.momeokji.aiDiarybackend.support.TestSocialTokens;

class AppleIdTokenVerifierTest {

	static final String CLIENT_ID = "com.momeokji.cake";
	static final String RAW_NONCE = "raw-n";
	static final String NONCE_HASH = RefreshTokens.sha256Hex(RAW_NONCE);

	final AppleIdTokenVerifier verifier = new AppleIdTokenVerifier(TestSocialTokens.appleDecoder(CLIENT_ID));

	@Test
	void validTokenWithMatchingNonce() {
		SocialIdentity id = verifier.verify(TestSocialTokens.apple("sub-a", CLIENT_ID, NONCE_HASH, null), RAW_NONCE);

		assertThat(id.provider()).isEqualTo(SocialProvider.APPLE);
		assertThat(id.providerUserId()).isEqualTo("sub-a");
		assertThat(id.email()).isNull();
	}

	@Test
	void emailIsCarriedWhenPresent() {
		SocialIdentity id = verifier.verify(TestSocialTokens.apple("sub-a", CLIENT_ID, NONCE_HASH, "hidden@privaterelay.appleid.com"), RAW_NONCE);

		assertThat(id.email()).isEqualTo("hidden@privaterelay.appleid.com");
	}

	@Test
	void nonceMismatchIsInvalid() {
		assertInvalid(TestSocialTokens.apple("sub-a", CLIENT_ID, NONCE_HASH, null), "other");
	}

	@Test
	void missingNonceIsInvalid() {
		assertInvalid(TestSocialTokens.apple("sub-a", CLIENT_ID, null, null), RAW_NONCE);
	}

	@Test
	void missingSubIsInvalid() {
		assertInvalid(TestSocialTokens.appleWithoutSub(CLIENT_ID, NONCE_HASH), RAW_NONCE);
	}

	@Test
	void wrongIssuerIsInvalid() {
		assertInvalid(TestSocialTokens.appleWithIssuer("https://accounts.google.com", "sub-a", CLIENT_ID, NONCE_HASH), RAW_NONCE);
	}

	@Test
	void wrongAudIsInvalid() {
		assertInvalid(TestSocialTokens.apple("sub-a", "com.other.app", NONCE_HASH, null), RAW_NONCE);
	}

	private void assertInvalid(String token, String rawNonce) {
		assertThatThrownBy(() -> verifier.verify(token, rawNonce))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID);
	}
}
