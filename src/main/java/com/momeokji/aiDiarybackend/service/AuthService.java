package com.momeokji.aiDiarybackend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.common.util.JwtUtil;
import com.momeokji.aiDiarybackend.dto.request.MemberSignupRequestDto;
import com.momeokji.aiDiarybackend.dto.response.TokenResponseDto;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
	private final MemberRepository memberRepository;
	private final JwtUtil jwt;

	@Transactional
	public TokenResponseDto issueTokens(MemberSignupRequestDto req) {
		Member member = memberRepository.findByDeviceId(req.getDeviceId())
			.orElseGet(() -> memberRepository.save(
				Member.builder()
					.memberId(UUID.randomUUID().toString())
					.deviceId(req.getDeviceId())
					.osType(req.getOsType())
					.preference(req.getPreference())
					.build()
			));

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
