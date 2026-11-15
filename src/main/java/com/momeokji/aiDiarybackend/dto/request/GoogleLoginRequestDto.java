package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code POST /auth/social/google} 요청(스펙 6.1). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GoogleLoginRequestDto {

	@NotBlank(message = "idToken은 필수입니다.")
	private String idToken;

	@NotBlank(message = "deviceId는 필수입니다.")
	@Pattern(regexp = RequestPatterns.UUID, message = RequestPatterns.UUID_MESSAGE)
	private String deviceId;

	@NotBlank(message = "os는 필수입니다.")
	@Pattern(regexp = RequestPatterns.OS, message = RequestPatterns.OS_MESSAGE)
	private String os;

	/** 없을 수 있다(푸시 권한 거부 등). */
	private String fcmToken;
}
