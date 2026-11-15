package com.momeokji.aiDiarybackend.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.momeokji.aiDiarybackend.service.auth.apple.AppleAuthClient;

/** 애플 HTTP 호출을 대신한다. 교환 결과를 지정하고 revoke 호출을 기록한다. */
public class FakeAppleAuthClient implements AppleAuthClient {

	public Optional<String> exchangeResult = Optional.of("apple-rt");
	public boolean revokeResult = true;
	public final List<String> exchangedCodes = new ArrayList<>();
	public final List<String> revoked = new ArrayList<>();

	public void reset() {
		exchangeResult = Optional.of("apple-rt");
		revokeResult = true;
		exchangedCodes.clear();
		revoked.clear();
	}

	@Override
	public Optional<String> exchangeAuthorizationCode(String authorizationCode) {
		exchangedCodes.add(authorizationCode);
		return exchangeResult;
	}

	@Override
	public boolean revoke(String appleRefreshToken) {
		revoked.add(appleRefreshToken);
		return revokeResult;
	}
}
