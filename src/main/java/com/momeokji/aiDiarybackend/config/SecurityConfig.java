package com.momeokji.aiDiarybackend.config;

import java.util.List;

import com.momeokji.aiDiarybackend.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

	private final JwtAuthFilter jwtAuthFilter;

	/** false 로 두면 인증 없이 열린다. 로컬 개발용이며 배포 환경에서는 켜 둔다. */
	@Value("${swagger.auth.enabled:true}")
	private boolean swaggerAuthEnabled;

	@Value("${swagger.auth.username:}")
	private String swaggerUsername;

	@Value("${swagger.auth.password:}")
	private String swaggerPassword;

	/**
	 * Swagger 전용 필터 체인. JWT 를 쓰는 아래 {@link #filterChain} 보다 먼저 적용되며,
	 * {@link #SWAGGER_PATHS} 에만 관여하므로 일반 API 동작에는 영향을 주지 않는다.
	 *
	 * <p>springdoc 이 꺼져 있으면 이 빈 자체가 등록되지 않는다.
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

	@Bean
	@Order(2)
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/users").permitAll()
				.requestMatchers(HttpMethod.POST, "/admins").permitAll()
				.requestMatchers(HttpMethod.POST, "/admins/login").permitAll()
				.requestMatchers( "/auth/refresh","/auth/login","/error", "/error/**", "/health").permitAll()
				.anyRequest().authenticated()
			)
			.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
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
		cfg.setExposedHeaders(List.of("Authorization","Refresh-Token")); // JWT 읽어야 하면
		cfg.setAllowCredentials(true);
		cfg.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cfg);
		return source;
	}


}
