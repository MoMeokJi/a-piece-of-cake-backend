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
 * <p>이 앱은 {@code Authorization: Bearer <access token>} 헤더로 인증하고, 액세스 토큰이 만료된
 * 경우에는 {@code Refresh-Token} 헤더를 함께 본다({@code JwtAuthFilter} 참고). Swagger UI 의
 * Authorize 버튼에서 두 가지를 모두 입력할 수 있도록 스킴을 두 개 등록한다.
 */
@Configuration
public class OpenApiConfig {

	private static final String ACCESS_TOKEN_SCHEME = "accessToken";
	private static final String REFRESH_TOKEN_SCHEME = "refreshToken";

	@Bean
	public OpenAPI openAPI() {
		Info info = new Info()
			.title("A Piece of Cake API")
			.version("v1")
			.description("""
				AI 일기 서비스 백엔드 API 명세.

				인증이 필요한 API 를 호출하려면 오른쪽 위 Authorize 버튼을 눌러
				로그인으로 받은 액세스 토큰을 입력하세요. (Bearer 접두사는 자동으로 붙습니다)
				""");

		SecurityScheme accessToken = new SecurityScheme()
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT")
			.in(SecurityScheme.In.HEADER)
			.name("Authorization")
			.description("로그인 응답의 Authorization 헤더에서 받은 액세스 토큰");

		SecurityScheme refreshToken = new SecurityScheme()
			.type(SecurityScheme.Type.APIKEY)
			.in(SecurityScheme.In.HEADER)
			.name("Refresh-Token")
			.description("액세스 토큰이 만료됐을 때 재발급에 사용하는 리프레시 토큰");

		return new OpenAPI()
			.info(info)
			.components(new Components()
				.addSecuritySchemes(ACCESS_TOKEN_SCHEME, accessToken)
				.addSecuritySchemes(REFRESH_TOKEN_SCHEME, refreshToken))
			// 문서 전체에 기본 적용. 인증이 필요 없는 API 는 토큰을 보내도 그대로 동작한다.
			.addSecurityItem(new SecurityRequirement()
				.addList(ACCESS_TOKEN_SCHEME)
				.addList(REFRESH_TOKEN_SCHEME));
	}
}
