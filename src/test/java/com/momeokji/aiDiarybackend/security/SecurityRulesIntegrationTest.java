package com.momeokji.aiDiarybackend.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.momeokji.aiDiarybackend.config.JwtConfig;
import com.momeokji.aiDiarybackend.config.JwtProperties;
import com.momeokji.aiDiarybackend.entity.Admin;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.AdminRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class SecurityRulesIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	AccessTokenIssuer issuer;

	@Autowired
	JwtProperties jwtProperties;

	@Test
	void noTokenOnProtectedApiIsUnauthorized() throws Exception {
		mockMvc.perform(get("/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.instance").value("/users/me"))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")));
	}

	@Test
	void corsResponseDoesNotExposeAuthorizationHeader() throws Exception {
		// 토큰은 응답 본문으로 내려가므로 Authorization 응답 헤더를 노출할 이유가 없다.
		mockMvc.perform(get("/health").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
	}

	@Test
	void validMemberTokenReachesMe() throws Exception {
		String memberId = saveMember();

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issueForMember(memberId).value()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberId").value(memberId));
	}

	@Test
	void expiredAccessIsTokenExpired() throws Exception {
		String memberId = saveMember();
		AccessTokenIssuer past = new AccessTokenIssuer(jwtProperties, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-31)));

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + past.issueForMember(memberId).value()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
	}

	@Test
	void memberTokenOnAdminApiIsTokenInvalid() throws Exception {
		String memberId = saveMember();

		mockMvc.perform(get("/admin/admins").header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issueForMember(memberId).value()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void adminTokenOnAppApiIsTokenInvalid() throws Exception {
		Admin admin = saveAdmin("root", true, true);

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issueForAdmin(admin).value()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void tokenWithoutRoleIsRejected() throws Exception {
		String memberId = saveMember();
		String token = signWithMemberKeyWithoutRole(memberId);

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void withdrawnMemberTokenIsRejected() throws Exception {
		String memberId = saveMember();
		String token = issuer.issueForMember(memberId).value();
		memberRepository.softDeleteOne(memberId, LocalDateTime.now());

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void garbageBearerIsTokenInvalid() throws Exception {
		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void publicPathIgnoresExpiredBearer() throws Exception {
		AccessTokenIssuer past = new AccessTokenIssuer(jwtProperties, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-31)));

		mockMvc.perform(get("/health").header(HttpHeaders.AUTHORIZATION, "Bearer " + past.issueForMember("whoever").value()))
			.andExpect(status().isOk());
	}

	@Test
	void oldAdminPathsAreGone() throws Exception {
		Admin admin = saveAdmin("root", true, true);

		mockMvc.perform(get("/dashboards/main").header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issueForAdmin(admin).value()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void adminTokenReachesAdminApi() throws Exception {
		Admin admin = saveAdmin("root", true, true);

		mockMvc.perform(get("/admin/dashboards/main").header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issueForAdmin(admin).value()))
			.andExpect(status().isOk());
	}

	@Test
	void adminTokenWithStaleVersionIsTokenInvalid() throws Exception {
		Admin admin = saveAdmin("root", true, true);
		String token = issuer.issueForAdmin(admin).value();
		admin.bumpTokenVersion();
		adminRepository.save(admin);

		mockMvc.perform(get("/admin/dashboards/main").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	private String saveMember() {
		String memberId = java.util.UUID.randomUUID().toString();
		memberRepository.save(Member.create(memberId, false));
		return memberId;
	}

	private Admin saveAdmin(String adminId, boolean isValid, boolean isSuper) {
		return adminRepository.save(Admin.builder()
			.adminId(adminId).adminPwd("{noop}x").createdAt(LocalDateTime.now())
			.isValid(isValid).isSuper(isSuper).build());
	}

	private String signWithMemberKeyWithoutRole(String subject) {
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtConfig.hmacKey(jwtProperties.member().secret())));
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(jwtProperties.issuer()).subject(subject).audience(List.of("app"))
			.issuedAt(now).expiresAt(now.plusSeconds(600)).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}
}
