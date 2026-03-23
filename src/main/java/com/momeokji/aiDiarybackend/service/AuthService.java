package com.momeokji.aiDiarybackend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import com.momeokji.aiDiarybackend.common.util.JwtUtil;
import com.momeokji.aiDiarybackend.dto.request.MemberSignupRequestDto;
import com.momeokji.aiDiarybackend.dto.response.TokenResponseDto;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.DiaryColorRepository;
import com.momeokji.aiDiarybackend.repository.DiaryImageRepository;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.seed.RefsetSeedLoader;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
	private final MemberRepository memberRepository;
	private final JwtUtil jwt;
	private final RedisService redisService;
	private final RefsetSeedLoader refsetSeedLoader;
	private final DiaryRepository diaryRepository;
	private final DiaryColorRepository diaryColorRepository;
	private final DiaryImageRepository diaryImageRepository;

	@Transactional
	public TokenResponseDto issueTokens(MemberSignupRequestDto req) {
		Member member = memberRepository.findByDeviceIdAndIsValidTrue(req.getDeviceId()).orElse(null);
		boolean isNew = false;

		if (member == null) {
			member = memberRepository.save(
				Member.builder()
					.memberId(UUID.randomUUID().toString())
					.deviceId(req.getDeviceId())
					.mobileOS(req.getMobileOS())
					.preference(req.getPreference())
					.lastActiveAt(LocalDateTime.now())
					.build()
			);
			isNew = true;
		}
		else{
			//기존 유저면 로그인 시 lastActiveTime변경
			memberRepository.changeLastActiveAt(member.getMemberId(), LocalDateTime.now());
		}
		// 신규 유저면 refsets 시드 삽입
		if (isNew) {
			var seed = refsetSeedLoader.loadSeedItemsAsJsonStrings();
			redisService.seedDefaultsIfEmpty(member.getMemberId(), seed);
		}
		return TokenResponseDto.builder()
			.accessToken(jwt.generateAccessToken(member))
			.refreshToken(jwt.generateRefreshToken(member))
			.build();
	}

	public TokenResponseDto refresh(String refreshToken) {
		var jws = jwt.parse(refreshToken);
		var claims = jws.getPayload();

		if (!"REFRESH".equals(claims.get("typ"))){
			throw new IllegalArgumentException("refresh가 유효하지 않습니다.");
		}

		String userId = claims.getSubject();
		Member member = memberRepository.findByMemberIdAndIsValidTrue(userId).orElseThrow(()->new IllegalArgumentException("탈퇴 하거나 유효하지 않은 계정"));

		memberRepository.changeLastActiveAt(userId, LocalDateTime.now());

		return TokenResponseDto.builder()
			.accessToken(jwt.generateAccessToken(member))
			.refreshToken(jwt.generateRefreshToken(member))
			.build();
	}


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

	@Transactional
	public TokenResponseDto loginByDeviceId(String deviceId) {
		Member member = memberRepository.findByDeviceIdAndIsValidTrue(deviceId)
			.orElseThrow(() -> new IllegalArgumentException("DeviceId가 유효하지 않습니다."));

		memberRepository.changeLastActiveAt(member.getMemberId(), LocalDateTime.now());

		return TokenResponseDto.builder()
			.accessToken(jwt.generateAccessToken(member))
			.refreshToken(jwt.generateRefreshToken(member))
			.build();
	}



}
