package com.momeokji.aiDiarybackend.dto.response;

import com.momeokji.aiDiarybackend.entity.Member;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** {@code GET /users/me} 응답. {@code nickname} 은 아직 정하지 않았으면 null 이다. */
@Getter
@AllArgsConstructor
public class UserMeResponseDto {

	private String memberId;
	private String nickname;

	public static UserMeResponseDto from(Member member) {
		return new UserMeResponseDto(member.getMemberId(), member.getNickname());
	}
}
