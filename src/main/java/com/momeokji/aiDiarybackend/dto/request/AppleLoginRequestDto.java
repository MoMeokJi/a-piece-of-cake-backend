package com.momeokji.aiDiarybackend.dto.request;

import java.util.List;

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

	/**
	 * 약관 시트에서 체크한 약관 ID({@code GET /auth/terms} 의 {@code id}). 신규 가입이면 필수 약관이 모두 들어 있어야 하고,
	 * 빠지면 400 {@code TERMS_AGREEMENT_REQUIRED} 다. 기존 회원의 로그인에서는 읽지 않는다.
	 */
	private List<Long> agreedTermsIds;
}
