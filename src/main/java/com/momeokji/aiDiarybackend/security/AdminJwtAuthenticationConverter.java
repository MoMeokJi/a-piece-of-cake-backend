package com.momeokji.aiDiarybackend.security;

import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.momeokji.aiDiarybackend.entity.Admin;
import com.momeokji.aiDiarybackend.repository.AdminRepository;

/**
 * 관리자 access 토큰 → 인증 객체(스펙 4.4, 7.2). {@code role ∈ {ADMIN, SUPER_ADMIN}} 이고, 관리자가 승인
 * 상태이며 삭제되지 않았고, {@code ver} 가 현재 token_version 과 같아야 한다.
 * {@code Authentication.getName()} 은 admin_id 다.
 */
public class AdminJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN");

	private final AdminRepository adminRepository;

	public AdminJwtAuthenticationConverter(AdminRepository adminRepository) {
		this.adminRepository = adminRepository;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		String role = jwt.getClaimAsString(AccessTokenIssuer.ROLE_CLAIM);
		if (role == null || !ADMIN_ROLES.contains(role)) {
			throw new InvalidBearerTokenException("관리자 토큰이 아닙니다.");
		}
		String adminId = jwt.getSubject();
		Admin admin = adminId == null ? null : adminRepository.findByAdminIdAndIsValidTrue(adminId).orElse(null);
		if (admin == null || admin.getDeletedAt() != null) {
			throw new InvalidBearerTokenException("유효하지 않은 관리자입니다.");
		}
		Long version = jwt.getClaim(AccessTokenIssuer.VERSION_CLAIM) instanceof Number n ? n.longValue() : null;
		long current = admin.getTokenVersion() == null ? 0L : admin.getTokenVersion().longValue();
		if (version == null || version != current) {
			throw new InvalidBearerTokenException("만료된 관리자 세션입니다.");
		}
		return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)), adminId);
	}
}
