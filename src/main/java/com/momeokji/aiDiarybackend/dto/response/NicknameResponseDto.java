package com.momeokji.aiDiarybackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** {@code PUT /users/me/nickname} 응답. 저장된 닉네임을 그대로 돌려준다. */
@Getter
@AllArgsConstructor
public class NicknameResponseDto {

	private String nickname;
}
