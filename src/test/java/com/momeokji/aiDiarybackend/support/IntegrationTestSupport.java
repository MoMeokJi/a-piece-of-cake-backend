package com.momeokji.aiDiarybackend.support;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
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

	@MockitoBean
	protected RedisService redisService;

	@MockitoBean
	protected FcmSender fcmSender;

	@Autowired
	protected FakeAppleAuthClient fakeAppleAuthClient;

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
