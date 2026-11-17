package com.momeokji.aiDiarybackend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

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

	@Value("${diary.daily-limit:3}")
	private int dailyLimit;

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

				## 공통 규칙

				- 시각(`createdAt` 등)은 ISO 8601 에 KST 오프셋을 붙인 문자열입니다. 예: `2025-09-16T21:12:00+09:00`. \
				  오늘·이번 달·연속 일수 같은 날짜 경계는 모두 KST 기준입니다.
				- 목록 정렬 `sort` 는 `recent`(최신순, 기본) 또는 `oldest`(오래된순)입니다. `created` 는 `oldest` 의 별칭입니다.
				- 하루(KST)에 쓸 수 있는 일기는 %d개입니다(`GET /users/me/stats` 의 `dailyLimit`). 초과하면 429 입니다.

				## 에러 응답

				RFC 9457 Problem Details 형식이며 클라이언트는 `code` 필드로만 분기합니다. `detail` 은 사람이 읽는 설명입니다.

				```json
				{ "type": "about:blank", "title": "Unauthorized", "status": 401, "detail": "access token이 만료되었습니다.", "instance": "/users/me", "code": "TOKEN_EXPIRED" }
				```

				| HTTP | code | 발생 조건 | 앱 권장 동작 |
				|---|---|---|---|
				| 401 | `UNAUTHORIZED` | 보호된 API 에 토큰 없음 | 로그인 화면 |
				| 401 | `TOKEN_EXPIRED` | access 토큰 만료 (서명·발급자는 정상) | `POST /auth/refresh` 1회 후 원요청 재시도 |
				| 401 | `TOKEN_INVALID` | access 토큰 서명·iss·aud 불일치, role 없음, 탈퇴한 회원 | 저장된 토큰 삭제 후 로그인 화면 |
				| 401 | `REFRESH_INVALID` | refresh 토큰 없음·만료·deviceId 불일치 | 저장된 토큰 삭제 후 로그인 화면 |
				| 401 | `REFRESH_REUSED` | refresh 토큰 재사용 감지로 세션 삭제 | `REFRESH_INVALID` 와 동일 |
				| 401 | `SOCIAL_TOKEN_INVALID` | 소셜 토큰 검증 실패, nonce 불일치 | 로그인 실패 안내 |
				| 401 | `ADMIN_LOGIN_FAILED` | 관리자 아이디 또는 비밀번호 틀림 | 안내 |
				| 403 | `FORBIDDEN` | 권한 부족 | 안내 |
				| 403 | `ADMIN_NOT_APPROVED` | 승인 대기 관리자 | 안내 |
				| 400 | `VALIDATION_FAILED` | 요청 값 형식 오류 (닉네임 규칙 위반 포함) | 입력 안내 |
				| 400 | `BAD_REQUEST` | 그 외 잘못된 요청 (허용되지 않는 `sort` 값 등) | 안내 |
				| 404 | `DIARY_NOT_FOUND` | 내 일기가 아니거나 없거나 삭제됨 | 목록으로 |
				| 404 | `DEVICE_NOT_FOUND` | 내 소유가 아닌 기기의 FCM 갱신 | 다시 로그인 |
				| 404 | `MEMBER_NOT_FOUND` | 회원 없음 (방어용) | 로그인 화면 |
				| 429 | `DIARY_DAILY_LIMIT_EXCEEDED` | 오늘(KST) 작성 개수가 제한에 도달 | 작성 불가 안내 |
				| 503 | `SOCIAL_PROVIDER_UNAVAILABLE` | 소셜 공급자 키 조회 실패 | 잠시 후 재시도 |
				| 500 | `INTERNAL_ERROR` | 그 외 | 안내 |

				위 표 밖의 Spring MVC 표준 오류(없는 경로 404 `NOT_FOUND`, 405 `METHOD_NOT_ALLOWED`, 415 `UNSUPPORTED_MEDIA_TYPE`,
				본문 파싱 실패 400 `VALIDATION_FAILED` 등)는 HTTP 상태 이름이 그대로 `code` 가 됩니다. 앱은 이들을 일반 오류로 처리하면 됩니다.

				`TOKEN_EXPIRED` 와 `TOKEN_INVALID` 는 서로 다른 코드입니다. 앱이 401 에 무조건 refresh 를 한 번 시도하는 방식도 \
				동작하지만, `TOKEN_INVALID` 는 refresh 해도 같은 결과이므로 바로 로그아웃 처리하는 편이 빠릅니다.
				""".formatted(dailyLimit));

		SecurityScheme accessToken = new SecurityScheme()
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT")
			.in(SecurityScheme.In.HEADER)
			.name("Authorization")
			.description("소셜 로그인 / refresh / 관리자 로그인 응답 바디의 accessToken");

		return new OpenAPI()
			.info(info)
			// 운영은 앞단에서 https 를 끝내고 http 로 넘겨서, 비워 두면 springdoc 이 http 주소를 적는다.
			// 그러면 https 로 연 스웨거의 요청을 브라우저가 막는다. 상대 주소는 연 페이지의 주소를 그대로 쓴다.
			.servers(List.of(new Server().url("/")))
			.components(new Components()
				.addSecuritySchemes(ACCESS_TOKEN_SCHEME, accessToken))
			// 문서 전체에 기본 적용. 인증이 필요 없는 API 는 토큰을 보내도 그대로 동작한다.
			.addSecurityItem(new SecurityRequirement().addList(ACCESS_TOKEN_SCHEME));
	}
}
