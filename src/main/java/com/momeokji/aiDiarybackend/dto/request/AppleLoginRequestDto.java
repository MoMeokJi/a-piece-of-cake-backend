package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code POST /auth/social/apple} 요청(스펙 6.1). nonce 는 앱이 만든 원본 값이다(해시가 아님). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AppleLoginRequestDto {

	@NotBlank(message = "identityToken은 필수입니다.")
	private String identityToken;

	@NotBlank(message = "authorizationCode는 필수입니다.")
	private String authorizationCode;

	@NotBlank(message = "nonce는 필수입니다.")
	private String nonce;

	@NotBlank(message = "deviceId는 필수입니다.")
	@Pattern(regexp = RequestPatterns.UUID, message = RequestPatterns.UUID_MESSAGE)
	private String deviceId;

	@NotBlank(message = "os는 필수입니다.")
	@Pattern(regexp = RequestPatterns.OS, message = RequestPatterns.OS_MESSAGE)
	private String os;

	private String fcmToken;
}
