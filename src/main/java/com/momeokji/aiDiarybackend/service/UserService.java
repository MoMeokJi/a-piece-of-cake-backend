package com.momeokji.aiDiarybackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** 내 정보(회원 프로필). 인증을 통과한 회원만 들어오므로 못 찾는 경우는 방어용이다. */
@Service
@RequiredArgsConstructor
public class UserService {

	private final MemberRepository memberRepository;

	@Transactional(readOnly = true)
	public Member getMe(String memberId) {
		return findValid(memberId);
	}

	/** 닉네임 설정과 수정. 형식 검증은 요청 DTO 가 한다. */
	@Transactional
	public Member changeNickname(String memberId, String nickname) {
		Member member = findValid(memberId);
		member.changeNickname(nickname);
		return member;
	}

	private Member findValid(String memberId) {
		return memberRepository.findByMemberIdAndIsValidTrue(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
	}
}
