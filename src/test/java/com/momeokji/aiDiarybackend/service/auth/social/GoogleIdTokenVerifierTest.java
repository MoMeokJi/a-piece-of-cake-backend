package com.momeokji.aiDiarybackend.service.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.entity.SocialProvider;
import com.momeokji.aiDiarybackend.support.TestSocialTokens;
import com.nimbusds.jose.RemoteKeySourceException;

class GoogleIdTokenVerifierTest {

	final GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
		TestSocialTokens.googleDecoder(List.of("ios-client", "android-server-client")));

	@Test
	void validTokenYieldsIdentity() {
		SocialIdentity id = verifier.verify(TestSocialTokens.google("sub-1", "ios-client", "a@x.com"));

		assertThat(id.provider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(id.providerUserId()).isEqualTo("sub-1");
		assertThat(id.email()).isEqualTo("a@x.com");
	}

	@Test
	void androidServerClientAudIsAccepted() {
		SocialIdentity id = verifier.verify(TestSocialTokens.google("sub-2", "android-server-client", null));

		assertThat(id.providerUserId()).isEqualTo("sub-2");
		assertThat(id.email()).isNull();
	}

	@Test
	void legacyIssuerWithoutSchemeIsAccepted() {
		SocialIdentity id = verifier.verify(TestSocialTokens.googleWithIssuer("accounts.google.com", "sub-3", "ios-client"));

		assertThat(id.providerUserId()).isEqualTo("sub-3");
	}

	@Test
	void unknownAudIsInvalid() {
		assertInvalid(TestSocialTokens.google("sub-1", "other", null));
	}

	@Test
	void unknownIssuerIsInvalid() {
		assertInvalid(TestSocialTokens.googleWithIssuer("https://evil.example", "sub-1", "ios-client"));
	}

	@Test
	void expiredIsInvalid() {
		assertInvalid(TestSocialTokens.expiredGoogle("sub-1", "ios-client"));
	}

	@Test
	void otherSignatureIsInvalid() {
		assertInvalid(TestSocialTokens.googleSignedByOtherKey("sub-1", "ios-client"));
	}

	@Test
	void garbageIsInvalid() {
		assertInvalid("not-a-jwt");
	}

	@Test
	void jwksFetchFailureIsProviderUnavailable() {
		JwtDecoder down = token -> {
			throw new JwtException("Couldn't retrieve remote JWK set", new RemoteKeySourceException("down", null));
		};
		GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(down);

		assertThatThrownBy(() -> verifier.verify("any"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
	}

	private void assertInvalid(String token) {
		assertThatThrownBy(() -> verifier.verify(token))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID);
	}
}
