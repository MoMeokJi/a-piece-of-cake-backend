package com.momeokji.aiDiarybackend.support;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.entity.Admin;
import com.momeokji.aiDiarybackend.repository.AdminRepository;
import com.momeokji.aiDiarybackend.service.RedisService;
import com.momeokji.aiDiarybackend.service.push.FcmSender;

/**
 * 통합 테스트 베이스. H2(MySQL 모드) 위에서 전체 컨텍스트를 띄우고 MockMvc 로 호출한다.
 *
 * <p>클래스에 {@code @Transactional} 을 걸지 않는다. 서비스 쪽의 REQUIRES_NEW 트랜잭션이
 * 테스트 트랜잭션 밖에서 커밋되어야 하기 때문이다. 대신 각 테스트가 끝날 때 모든 테이블을 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSocialConfig.class)
public abstract class IntegrationTestSupport {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AdminRepository adminRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	protected RedisService redisService;

	@MockitoBean
	protected FcmSender fcmSender;

	@Autowired
	protected FakeAppleAuthClient fakeAppleAuthClient;

	/** 테스트 전용 기기 ID. 회원마다 다른 기기가 필요하면 각 테스트가 직접 만든다. */
	protected static final String TEST_DEVICE_ID = "11111111-1111-1111-1111-111111111111";

	/** 구글 소셜 로그인으로 회원을 만들고(이미 있으면 로그인) access 토큰을 돌려준다. */
	protected String loginAsMember(String sub) throws Exception {
		Map<String, Object> req = Map.of(
			"idToken", TestSocialTokens.google(sub, "ios-client", sub + "@gmail.com"),
			"deviceId", TEST_DEVICE_ID,
			"os", "IOS");
		String body = mockMvc.perform(MockMvcRequestBuilders.post("/auth/social/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(MockMvcResultMatchers.status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("accessToken").asText();
	}

	/** 승인된 일반 관리자 하나를 만들고(없으면) 로그인해 관리자 access 토큰을 돌려준다. */
	protected String loginAsAdmin() throws Exception {
		if (adminRepository.findByAdminId("admin-test").isEmpty()) {
			adminRepository.save(Admin.builder()
				.adminId("admin-test").adminPwd(passwordEncoder.encode("pw-admin")).createdAt(LocalDateTime.now())
				.isValid(true).isSuper(false).build());
		}
		String body = mockMvc.perform(MockMvcRequestBuilders.post("/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("adminId", "admin-test", "adminPwd", "pw-admin"))))
			.andExpect(MockMvcResultMatchers.status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("accessToken").asText();
	}

	@AfterEach
	void cleanDatabase() {
		fakeAppleAuthClient.reset();
		jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
		// DATABASE_TO_LOWER=TRUE 라서 스키마 이름이 'public' 으로 저장된다. 문자열 상수 대신 현재 스키마를 쓴다.
		List<String> tables = jdbcTemplate.queryForList(
			"SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_TYPE = 'BASE TABLE'",
			String.class);
		for (String table : tables) {
			jdbcTemplate.execute("TRUNCATE TABLE " + table);
		}
		jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
	}
}
