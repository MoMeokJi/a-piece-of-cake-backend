package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code POST /auth/refresh} 요청. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RefreshTokenRequestDto {

	@NotBlank(message = "refreshToken은 필수입니다.")
	private String refreshToken;

	@NotBlank(message = "deviceId는 필수입니다.")
	@Pattern(regexp = RequestPatterns.UUID, message = RequestPatterns.UUID_MESSAGE)
	private String deviceId;
}
