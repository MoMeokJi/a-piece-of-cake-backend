package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminLoginResultDto {

	private String accessToken;
	private Boolean isSuper;
}