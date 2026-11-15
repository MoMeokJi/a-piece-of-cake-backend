package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 관리자 로그인 응답(스펙 6.3). 토큰은 헤더가 아니라 바디로 준다. refresh 는 없다. */
@Getter
@Builder
public class AdminLoginResponseDto {

	private String accessToken;
	private long accessTokenExpiresIn;
	private Boolean isSuper;
}
