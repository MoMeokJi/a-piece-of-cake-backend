package com.momeokji.aiDiarybackend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import com.momeokji.aiDiarybackend.config.JwtConfig;
import com.momeokji.aiDiarybackend.config.JwtProperties;
import com.momeokji.aiDiarybackend.entity.Admin;

class AccessTokenIssuerTest {

	static final String MEMBER_SECRET = "member-secret-0123456789-0123456789-abc";
	static final String ADMIN_SECRET = "admin-secret-0123456789-0123456789-xyz";

	final JwtProperties props = new JwtProperties(
		"test-issuer",
		new JwtProperties.MemberToken(MEMBER_SECRET, Duration.ofMinutes(30), Duration.ofDays(60)),
		new JwtProperties.AdminToken(ADMIN_SECRET, Duration.ofHours(2)));

	// 디코더는 실제 시계로 만료를 검사하므로 발급 시계는 "지금" 에 고정한다
	final Clock fixedClock = Clock.fixed(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS), ZoneOffset.UTC);
	final AccessTokenIssuer issuer = new AccessTokenIssuer(props, fixedClock);
	final JwtDecoder memberDecoder = new JwtConfig().memberJwtDecoder(props);
	final JwtDecoder adminDecoder = new JwtConfig().adminJwtDecoder(props);

	@Test
	void memberTokenHasSpecClaims() {
		Jwt jwt = memberDecoder.decode(issuer.issueForMember("m-1").value());

		assertThat(jwt.getSubject()).isEqualTo("m-1");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("test-issuer");
		assertThat(jwt.getAudience()).containsExactly("app");
		assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
		assertThat(jwt.getClaims()).doesNotContainKey("did");
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
	}

	@Test
	void memberTokenExpiresIn30Minutes() {
		IssuedAccessToken token = issuer.issueForMember("m-1");
		Jwt jwt = memberDecoder.decode(token.value());

		assertThat(token.expiresInSeconds()).isEqualTo(1800);
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
	}

	@Test
	void memberTokenIsRejectedByAdminDecoder() {
		String memberToken = issuer.issueForMember("m-1").value();

		assertThatThrownBy(() -> adminDecoder.decode(memberToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void adminTokenIsRejectedByMemberDecoder() {
		String adminToken = issuer.issueForAdmin(admin("root", true, 0)).value();

		assertThatThrownBy(() -> memberDecoder.decode(adminToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void adminTokenCarriesRoleAndVersion() {
		IssuedAccessToken token = issuer.issueForAdmin(admin("root", true, 3));
		Jwt jwt = adminDecoder.decode(token.value());

		assertThat(jwt.getSubject()).isEqualTo("root");
		assertThat(jwt.getAudience()).containsExactly("admin");
		assertThat(jwt.getClaimAsString("role")).isEqualTo("SUPER_ADMIN");
		assertThat(jwt.getClaims().get("ver")).isEqualTo(3L);
		assertThat(token.expiresInSeconds()).isEqualTo(7200);
	}

	@Test
	void regularAdminGetsAdminRole() {
		Jwt jwt = adminDecoder.decode(issuer.issueForAdmin(admin("staff", false, 0)).value());

		assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
	}

	@Test
	void expiredTokenIsRejected() {
		// 디코더는 실제 시계를 쓰므로 "지금 - 31분" 에 발급해 만료(30분 + 오차 60초)를 넘긴다
		AccessTokenIssuer oldIssuer = new AccessTokenIssuer(props, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-31)));

		assertThatThrownBy(() -> memberDecoder.decode(oldIssuer.issueForMember("m-1").value()))
			.isInstanceOf(JwtValidationException.class)
			.hasMessageContaining("Jwt expired at");
	}

	@Test
	void shortSecretFailsFast() {
		assertThatThrownBy(() -> JwtConfig.hmacKey("short")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void wrongIssuerIsRejected() {
		JwtProperties other = new JwtProperties("other-issuer", props.member(), props.admin());
		String token = new AccessTokenIssuer(other, fixedClock).issueForMember("m-1").value();

		assertThatThrownBy(() -> memberDecoder.decode(token)).isInstanceOf(JwtException.class);
	}

	private static Admin admin(String adminId, boolean isSuper, int tokenVersion) {
		Admin admin = Admin.builder()
			.adminId(adminId).adminPwd("x").createdAt(LocalDateTime.now())
			.isValid(true).isSuper(isSuper).build();
		for (int i = 0; i < tokenVersion; i++) {
			admin.bumpTokenVersion();
		}
		return admin;
	}
}
