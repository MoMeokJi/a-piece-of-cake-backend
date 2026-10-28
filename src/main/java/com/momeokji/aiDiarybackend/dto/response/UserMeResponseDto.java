package com.momeokji.aiDiarybackend.dto.response;

import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.entity.SocialProvider;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /users/me} 응답. {@code nickname} 은 아직 정하지 않았으면 null 이다.
 * {@code provider} 는 로그인 수단(GOOGLE / APPLE)으로 마이페이지의 "Google 계정으로 로그인 중" 에 쓴다.
 */
@Getter
@AllArgsConstructor
public class UserMeResponseDto {

	private String memberId;
	private String nickname;
	private SocialProvider provider;
	private boolean notificationEnabled;

	public static UserMeResponseDto from(Member member, SocialProvider provider) {
		return new UserMeResponseDto(member.getMemberId(), member.getNickname(), provider,
			Boolean.TRUE.equals(member.getNotificationEnabled()));
	}
}
