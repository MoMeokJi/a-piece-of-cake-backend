package com.momeokji.aiDiarybackend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.common.error.ProblemDetailFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security 단계의 401 을 Problem Details 로 응답한다(스펙 9.1).
 *
 * <ul>
 *   <li>디코더가 만료로 거부("Jwt expired at") → {@code TOKEN_EXPIRED}</li>
 *   <li>그 외 Bearer 토큰 오류(서명, iss/aud, role, 계정 무효, ver 불일치) → {@code TOKEN_INVALID}</li>
 *   <li>토큰 자체가 없음 → {@code UNAUTHORIZED}</li>
 * </ul>
 */
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final String EXPIRED_MARKER = "Jwt expired at";

	private final ObjectMapper objectMapper;

	public ProblemDetailsAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
		throws IOException {
		ErrorCode code = resolve(exception);
		String challenge = code == ErrorCode.UNAUTHORIZED ? "Bearer" : "Bearer error=\"invalid_token\"";

		response.setStatus(code.status().value());
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), ProblemDetailFactory.create(code, null, request.getRequestURI()));
	}

	static ErrorCode resolve(AuthenticationException exception) {
		if (!(exception instanceof OAuth2AuthenticationException)) {
			return ErrorCode.UNAUTHORIZED;
		}
		for (Throwable t = exception; t != null; t = t.getCause()) {
			if (t.getMessage() != null && t.getMessage().contains(EXPIRED_MARKER)) {
				return ErrorCode.TOKEN_EXPIRED;
			}
		}
		return ErrorCode.TOKEN_INVALID;
	}
}
