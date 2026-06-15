package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminLoginResponseDto {

	private Boolean isSuper;
}