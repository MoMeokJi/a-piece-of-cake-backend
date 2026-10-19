package com.momeokji.aiDiarybackend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.repository.DiaryColorRepository;
import com.momeokji.aiDiarybackend.repository.DiaryImageRepository;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/** 회원 탈퇴. 로그인/토큰 발급은 {@code service.auth} 패키지로 옮겨갔다. */
@Service
@RequiredArgsConstructor
public class AuthService {
	private final MemberRepository memberRepository;
	private final RedisService redisService;
	private final DiaryRepository diaryRepository;
	private final DiaryColorRepository diaryColorRepository;
	private final DiaryImageRepository diaryImageRepository;

	@Transactional
	public void withdraw(Authentication auth) {
		withdrawByUserId(auth.getName());
	}

	@Transactional
	public void withdrawByUserId(String userId) {

		List<Long> aliveDiaryIds = diaryRepository.findAliveIdsByUserId(userId);

		LocalDateTime now = LocalDateTime.now();

		if (!aliveDiaryIds.isEmpty()) {
			diaryColorRepository.softDeleteByDiaryIdIn(aliveDiaryIds, now);
			diaryImageRepository.softDeleteByDiaryIdIn(aliveDiaryIds, now);
			diaryRepository.softDeleteAllOfUser(userId, now);
		}
		memberRepository.softDeleteOne(userId, now);
		redisService.deleteUserData(userId);
	}
}
