package com.momeokji.aiDiarybackend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.momeokji.aiDiarybackend.service.auth.IssuedTokens;
import com.momeokji.aiDiarybackend.service.auth.SocialLoginResult;

import lombok.Builder;
import lombok.Getter;

/** 소셜 로그인 / refresh 응답(스펙 6.1). 토큰은 바디로만 전달한다. refresh 응답에는 isNewMember 가 없다. */
@Getter
@Builder
public class AuthTokenResponseDto {

	private String accessToken;
	private long accessTokenExpiresIn;
	private String refreshToken;
	private long refreshTokenExpiresIn;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Boolean isNewMember;

	public static AuthTokenResponseDto from(IssuedTokens tokens) {
		return builder()
			.accessToken(tokens.accessToken())
			.accessTokenExpiresIn(tokens.accessTokenExpiresIn())
			.refreshToken(tokens.refreshToken())
			.refreshTokenExpiresIn(tokens.refreshTokenExpiresIn())
			.build();
	}

	public static AuthTokenResponseDto from(SocialLoginResult result) {
		IssuedTokens tokens = result.tokens();
		return builder()
			.accessToken(tokens.accessToken())
			.accessTokenExpiresIn(tokens.accessTokenExpiresIn())
			.refreshToken(tokens.refreshToken())
			.refreshTokenExpiresIn(tokens.refreshTokenExpiresIn())
			.isNewMember(result.newMember())
			.build();
	}
}
