package com.momeokji.aiDiarybackend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.momeokji.aiDiarybackend.config.JwtConfig;
import com.momeokji.aiDiarybackend.config.JwtProperties;
import com.momeokji.aiDiarybackend.entity.Admin;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * access 토큰 발급기(스펙 4.1, 4.4).
 *
 * <p>앱: {@code aud=app, role=USER, sub=member_id}. 관리자: {@code aud=admin, role=ADMIN|SUPER_ADMIN, ver=token_version}.
 * 둘 다 HS256 이지만 서로 다른 키로 서명하므로 상대 체인에서는 검증에 실패한다.
 */
@Component
public class AccessTokenIssuer {

	public static final String MEMBER_AUDIENCE = JwtConfig.MEMBER_AUDIENCE;
	public static final String ADMIN_AUDIENCE = JwtConfig.ADMIN_AUDIENCE;
	public static final String ROLE_CLAIM = "role";
	public static final String VERSION_CLAIM = "ver";

	private final JwtProperties props;
	private final Clock clock;
	private final JwtEncoder memberEncoder;
	private final JwtEncoder adminEncoder;

	public AccessTokenIssuer(JwtProperties props, Clock clock) {
		this.props = props;
		this.clock = clock;
		this.memberEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtConfig.hmacKey(props.member().secret())));
		this.adminEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtConfig.hmacKey(props.admin().secret())));
	}

	public IssuedAccessToken issueForMember(String memberId) {
		Duration ttl = props.member().accessTokenTtl();
		JwtClaimsSet claims = base(memberId, MEMBER_AUDIENCE, ttl)
			.claim(ROLE_CLAIM, "USER")
			.build();
		return encode(memberEncoder, claims, ttl);
	}

	public IssuedAccessToken issueForAdmin(Admin admin) {
		Duration ttl = props.admin().accessTokenTtl();
		String role = Boolean.TRUE.equals(admin.getIsSuper()) ? "SUPER_ADMIN" : "ADMIN";
		JwtClaimsSet claims = base(admin.getAdminId(), ADMIN_AUDIENCE, ttl)
			.claim(ROLE_CLAIM, role)
			.claim(VERSION_CLAIM, admin.getTokenVersion() == null ? 0L : admin.getTokenVersion().longValue())
			.build();
		return encode(adminEncoder, claims, ttl);
	}

	private JwtClaimsSet.Builder base(String subject, String audience, Duration ttl) {
		Instant now = clock.instant();
		return JwtClaimsSet.builder()
			.issuer(props.issuer())
			.subject(subject)
			.audience(List.of(audience))
			.issuedAt(now)
			.expiresAt(now.plus(ttl));
	}

	private static IssuedAccessToken encode(JwtEncoder encoder, JwtClaimsSet claims, Duration ttl) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedAccessToken(value, ttl.toSeconds());
	}
}
