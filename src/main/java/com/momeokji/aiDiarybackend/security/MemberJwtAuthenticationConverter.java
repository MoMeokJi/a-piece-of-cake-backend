package com.momeokji.aiDiarybackend.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.momeokji.aiDiarybackend.repository.MemberRepository;

/**
 * 앱 access 토큰 → 인증 객체. {@code role=USER} 이고 회원이 살아 있어야 한다(스펙 7.2).
 * 탈퇴 직후 남은 access 로 30분 동안 일기를 쓰는 일을 막기 위해 매 요청 회원을 조회한다.
 * {@code Authentication.getName()} 은 member_id 다.
 */
public class MemberJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final List<SimpleGrantedAuthority> USER_AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

	private final MemberRepository memberRepository;

	public MemberJwtAuthenticationConverter(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		String role = jwt.getClaimAsString(AccessTokenIssuer.ROLE_CLAIM);
		if (!"USER".equals(role)) {
			throw new InvalidBearerTokenException("앱 유저 토큰이 아닙니다.");
		}
		String memberId = jwt.getSubject();
		if (memberId == null || memberRepository.findByMemberIdAndIsValidTrue(memberId).isEmpty()) {
			throw new InvalidBearerTokenException("탈퇴했거나 존재하지 않는 회원입니다.");
		}
		return new JwtAuthenticationToken(jwt, USER_AUTHORITIES, memberId);
	}
}
