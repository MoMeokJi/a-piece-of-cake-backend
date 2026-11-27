package com.momeokji.aiDiarybackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.dto.response.UserMeResponseDto;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.entity.SocialAccount;
import com.momeokji.aiDiarybackend.entity.SocialProvider;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.repository.SocialAccountRepository;

import lombok.RequiredArgsConstructor;

/** 내 정보(회원 프로필). 인증을 통과한 회원만 들어오므로 못 찾는 경우는 방어용이다. */
@Service
@RequiredArgsConstructor
public class UserService {

	private final MemberRepository memberRepository;
	private final SocialAccountRepository socialAccountRepository;

	/** 회원 하나에 소셜 계정은 하나다(탈퇴 후 재가입은 새 회원). 행이 없으면 provider 는 null. */
	@Transactional(readOnly = true)
	public UserMeResponseDto getMe(String memberId) {
		Member member = findValid(memberId);
		SocialProvider provider = socialAccountRepository.findByMemberId(memberId).stream()
			.findFirst()
			.map(SocialAccount::getProvider)
			.orElse(null);
		return UserMeResponseDto.from(member, provider);
	}

	/** 닉네임 설정과 수정. 형식 검증은 요청 DTO 가 한다. */
	@Transactional
	public Member changeNickname(String memberId, String nickname) {
		Member member = findValid(memberId);
		member.changeNickname(nickname);
		return member;
	}

	/** 기록 알림(리마인드·공감) 수신 여부. 마이페이지 토글이 쓴다. 가입 시점의 값은 약관 동의({@code SignupTerms})로 정해진다. */
	@Transactional
	public Member changeNotificationEnabled(String memberId, boolean enabled) {
		Member member = findValid(memberId);
		member.changeNotificationEnabled(enabled);
		return member;
	}

	private Member findValid(String memberId) {
		return memberRepository.findByMemberIdAndIsValidTrue(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
	}
}
