package com.momeokji.aiDiarybackend.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

	/**
	 * 약관 시트에서 체크한 약관 ID({@code GET /auth/terms} 의 {@code id}). 신규 가입이면 필수 약관이 모두 들어 있어야 하고,
	 * 빠지면 400 {@code TERMS_AGREEMENT_REQUIRED} 다. 기존 회원의 로그인에서는 값을 쓰지 않는다(없어도 된다).
	 * 다만 형식은 누구에게나 검사한다 — 숫자 배열이 아니거나 {@value RequestPatterns#MAX_AGREED_TERMS}개를 넘으면 400 {@code VALIDATION_FAILED}.
	 */
	@Size(max = RequestPatterns.MAX_AGREED_TERMS, message = "agreedTermsIds가 너무 많습니다.")
	private List<Long> agreedTermsIds;
}
