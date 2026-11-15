package com.momeokji.aiDiarybackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** {@code GET /app/min-version} 응답. 등록되지 않은 플랫폼은 null. */
@Getter
@AllArgsConstructor
public class MinVersionResponseDto {
	private AppVersionResponseDto ios;
	private AppVersionResponseDto android;
}
