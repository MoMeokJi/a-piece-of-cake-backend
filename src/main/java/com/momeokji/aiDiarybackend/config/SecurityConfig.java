package com.momeokji.aiDiarybackend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.repository.AdminRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.security.AdminJwtAuthenticationConverter;
import com.momeokji.aiDiarybackend.security.MemberJwtAuthenticationConverter;
import com.momeokji.aiDiarybackend.security.ProblemDetailsAccessDeniedHandler;
import com.momeokji.aiDiarybackend.security.ProblemDetailsAuthenticationEntryPoint;
import com.momeokji.aiDiarybackend.security.PublicPathBearerTokenResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Security 체인 구성(스펙 7.1, 7.2).
 *
 * <ol>
 *   <li>Swagger: 기존 그대로(HTTP Basic)</li>
 *   <li>관리자 {@code /admin/**}: 관리자 키 디코더. 가입/로그인만 공개, 나머지는 ADMIN 또는 SUPER_ADMIN</li>
 *   <li>앱(나머지 전부): 앱 키 디코더. 소셜 로그인/refresh/약관 목록(GET /auth/terms)/health/min-version/error 와 공개 사이트 경로(/, /privacy, /terms, /ai-consent)만 공개, 나머지는 USER</li>
 * </ol>
 *
 * <p>두 체인의 서명 키가 다르므로 관리자 토큰으로 앱 API 를, 앱 토큰으로 관리자 API 를 부를 수 없다.
 * 공개 경로에서는 Bearer 를 읽지 않아 만료 토큰을 붙인 refresh 요청도 정상 동작한다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	/** Swagger UI 와 명세(JSON/YAML)가 사용하는 경로. */
	private static final String[] SWAGGER_PATHS = {
		"/swagger-ui.html",
		"/swagger-ui/**",
		"/v3/api-docs",
		"/v3/api-docs/**",
		"/v3/api-docs.yaml"
	};

	private final MemberRepository memberRepository;
	private final AdminRepository adminRepository;
	private final ObjectMapper objectMapper;

	/** false 로 두면 인증 없이 열린다. 로컬 개발용이며 배포 환경에서는 켜 둔다. */
	@Value("${swagger.auth.enabled:true}")
	private boolean swaggerAuthEnabled;

	@Value("${swagger.auth.username:}")
	private String swaggerUsername;

	@Value("${swagger.auth.password:}")
	private String swaggerPassword;

	/**
	 * Swagger 전용 필터 체인. 아래 두 체인보다 먼저 적용되며 {@link #SWAGGER_PATHS} 에만 관여한다.
	 * springdoc 이 꺼져 있으면 이 빈 자체가 등록되지 않는다.
	 */
	@Bean
	@Order(1)
	@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true")
	public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher(SWAGGER_PATHS)
			.csrf(csrf -> csrf.disable())
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		if (!swaggerAuthEnabled) {
			log.info("Swagger UI 를 인증 없이 제공합니다. 배포 환경에서는 swagger.auth.enabled 를 켜 두세요.");
			http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
			return http.build();
		}

		// 인증을 켜 두었는데 계정 정보가 없으면, 열어두는 대신 막는다.
		// (환경변수 누락이 명세 공개로 이어지지 않도록)
		if (!StringUtils.hasText(swaggerUsername) || !StringUtils.hasText(swaggerPassword)) {
			log.warn("SWAGGER_USER / SWAGGER_PASSWORD 가 설정되지 않아 Swagger UI 접근을 차단합니다.");
			http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
			return http.build();
		}

		UserDetails docsUser = User.withUsername(swaggerUsername)
			.password(passwordEncoder().encode(swaggerPassword))
			.roles("DOCS")
			.build();

		http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
			.userDetailsService(new InMemoryUserDetailsManager(docsUser))
			.httpBasic(Customizer.withDefaults());
		return http.build();
	}

	/** 관리자 체인. {@code /admin/**} 전부가 자동으로 관리자 전용이 된다. */
	@Bean
	@Order(2)
	public SecurityFilterChain adminFilterChain(HttpSecurity http,
		@Qualifier("adminJwtDecoder") JwtDecoder adminJwtDecoder) throws Exception {

		RequestMatcher[] publicPaths = {
			matcher(HttpMethod.POST, "/admin/auth/signup"),
			matcher(HttpMethod.POST, "/admin/auth/login")
		};

		http.securityMatcher("/admin/**");
		common(http);
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers(publicPaths).permitAll()
				.anyRequest().hasAnyRole("ADMIN", "SUPER_ADMIN"))
			.oauth2ResourceServer(oauth2 -> oauth2
				.bearerTokenResolver(new PublicPathBearerTokenResolver(publicPaths))
				.jwt(jwt -> jwt
					.decoder(adminJwtDecoder)
					.jwtAuthenticationConverter(new AdminJwtAuthenticationConverter(adminRepository)))
				.authenticationEntryPoint(entryPoint())
				.accessDeniedHandler(accessDeniedHandler()));
		return http.build();
	}

	/** 앱 체인. 기본값이 ROLE_USER 라서 새 API 를 추가해도 인증이 자동으로 걸린다. */
	@Bean
	@Order(3)
	public SecurityFilterChain appFilterChain(HttpSecurity http,
		@Qualifier("memberJwtDecoder") JwtDecoder memberJwtDecoder) throws Exception {

		RequestMatcher[] publicPaths = {
			matcher(HttpMethod.POST, "/auth/social/**"),
			matcher(HttpMethod.POST, "/auth/refresh"),
			matcher(HttpMethod.GET, "/auth/terms"),
			matcher(HttpMethod.GET, "/health"),
			matcher(HttpMethod.GET, "/app/min-version"),
			// Google OAuth 동의 화면용 공개 사이트 경로
			matcher(HttpMethod.GET, "/"),
			matcher(HttpMethod.GET, "/privacy"),
			matcher(HttpMethod.GET, "/terms"),
			matcher(HttpMethod.GET, "/ai-consent"),
			matcher(null, "/error"),
			matcher(null, "/error/**")
		};

		common(http);
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers(publicPaths).permitAll()
				.anyRequest().hasRole("USER"))
			.oauth2ResourceServer(oauth2 -> oauth2
				.bearerTokenResolver(new PublicPathBearerTokenResolver(publicPaths))
				.jwt(jwt -> jwt
					.decoder(memberJwtDecoder)
					.jwtAuthenticationConverter(new MemberJwtAuthenticationConverter(memberRepository)))
				.authenticationEntryPoint(entryPoint())
				.accessDeniedHandler(accessDeniedHandler()));
		return http.build();
	}

	private void common(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(e -> e
				.authenticationEntryPoint(entryPoint())
				.accessDeniedHandler(accessDeniedHandler()));
	}

	private ProblemDetailsAuthenticationEntryPoint entryPoint() {
		return new ProblemDetailsAuthenticationEntryPoint(objectMapper);
	}

	private ProblemDetailsAccessDeniedHandler accessDeniedHandler() {
		return new ProblemDetailsAccessDeniedHandler(objectMapper);
	}

	private static RequestMatcher matcher(HttpMethod method, String pattern) {
		return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration cfg = new CorsConfiguration();
		// 개발용: 패턴 허용(요청 Origin을 그대로 에코, credentials 가능)
		cfg.setAllowedOriginPatterns(List.of(
			"http://localhost:*",
			"http://127.0.0.1:*",
			"http://192.168.*.*:*",
			"https://*.ngrok-free.dev",
			//운영용
			"https://piece-of-cake.click",
			"https://piece-of-cake.click:8080",
			"http://piece-of-cake.click",
			"http://piece-of-cake.click:8080"
		));
		cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
		cfg.addAllowedHeader("*");
		// 토큰은 응답 본문으로 내려가므로 노출할 응답 헤더가 없다.
		cfg.setAllowCredentials(true);
		cfg.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cfg);
		return source;
	}
}
