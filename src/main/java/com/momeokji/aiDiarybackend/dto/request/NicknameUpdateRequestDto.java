package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code PUT /users/me/nickname} 요청. 가입 흐름의 설정과 마이페이지의 수정이 같이 쓴다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NicknameUpdateRequestDto {

	@NotBlank(message = "nickname은 필수입니다.")
	@Pattern(regexp = RequestPatterns.NICKNAME, message = RequestPatterns.NICKNAME_MESSAGE)
	private String nickname;
}
