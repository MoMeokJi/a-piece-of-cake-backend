package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminLoginResultDto {

	private String accessToken;
	private String refreshToken;
	private Boolean isSuper;
}