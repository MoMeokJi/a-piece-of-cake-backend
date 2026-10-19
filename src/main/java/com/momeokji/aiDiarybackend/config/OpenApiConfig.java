package com.momeokji.aiDiarybackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger(OpenAPI) 문서 설정.
 *
 * <p>엔드포인트 목록은 컨트롤러에서 자동으로 수집되므로 여기서는 문서 제목과 인증 방식만 정의한다.
 *
 * <p>인증은 {@code Authorization: Bearer <access token>} 헤더 하나다. 앱 토큰은 소셜 로그인 또는
 * {@code POST /auth/refresh} 응답 바디의 {@code accessToken}, 관리자 토큰은 {@code POST /admin/auth/login}
 * 응답 바디의 {@code accessToken} 이다. 둘 다 같은 Bearer 스킴을 쓰지만 서명 키가 달라 서로의 API 에는 쓸 수 없다.
 */
@Configuration
public class OpenApiConfig {

	private static final String ACCESS_TOKEN_SCHEME = "accessToken";

	@Bean
	public OpenAPI openAPI() {
		Info info = new Info()
			.title("A Piece of Cake API")
			.version("v1")
			.description("""
				AI 일기 서비스 백엔드 API 명세.

				인증이 필요한 API 를 호출하려면 오른쪽 위 Authorize 버튼을 눌러 액세스 토큰을 입력하세요.
				(Bearer 접두사는 자동으로 붙습니다)

				- 앱: 소셜 로그인(`POST /auth/social/google`, `POST /auth/social/apple`) 또는 `POST /auth/refresh` 응답의 `accessToken`
				- 관리자: `POST /admin/auth/login` 응답의 `accessToken` (`/admin/**` 전용)

				에러 응답은 RFC 9457 Problem Details 형식이며 `code` 필드로 분기합니다.
				""");

		SecurityScheme accessToken = new SecurityScheme()
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT")
			.in(SecurityScheme.In.HEADER)
			.name("Authorization")
			.description("소셜 로그인 / refresh / 관리자 로그인 응답 바디의 accessToken");

		return new OpenAPI()
			.info(info)
			.components(new Components()
				.addSecuritySchemes(ACCESS_TOKEN_SCHEME, accessToken))
			// 문서 전체에 기본 적용. 인증이 필요 없는 API 는 토큰을 보내도 그대로 동작한다.
			.addSecurityItem(new SecurityRequirement().addList(ACCESS_TOKEN_SCHEME));
	}
}
