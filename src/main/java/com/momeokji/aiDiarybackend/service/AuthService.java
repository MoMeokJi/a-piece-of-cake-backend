package com.momeokji.aiDiarybackend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.common.util.JwtUtil;
import com.momeokji.aiDiarybackend.dto.request.MemberSignupRequestDto;
import com.momeokji.aiDiarybackend.dto.response.TokenResponseDto;
import com.momeokji.aiDiarybackend.entity.Member;
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

	@Transactional
	public TokenResponseDto issueTokens(MemberSignupRequestDto req) {
		Member member = memberRepository.findByDeviceId(req.getDeviceId()).orElse(null);
		boolean isNew = false;

		if (member == null) {
			member = memberRepository.save(
				Member.builder()
					.memberId(UUID.randomUUID().toString())
					.deviceId(req.getDeviceId())
					.osType(req.getOsType())
					.preference(req.getPreference())
					.build()
			);
			isNew = true;
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
		Member member = memberRepository.findById(userId).orElseThrow();

		return TokenResponseDto.builder()
			.accessToken(jwt.generateAccessToken(member))
			.refreshToken(jwt.generateRefreshToken(member))
			.build();
	}
}
