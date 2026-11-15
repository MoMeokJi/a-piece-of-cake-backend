package com.momeokji.aiDiarybackend.common.error;

import java.net.URI;

import org.springframework.http.ProblemDetail;

/**
 * RFC 9457 Problem Details 응답 생성. 확장 필드 {@code code} 를 항상 붙인다(스펙 9.1).
 */
public final class ProblemDetailFactory {

	public static final String CODE_PROPERTY = "code";

	private ProblemDetailFactory() {
	}

	public static ProblemDetail create(ErrorCode code, String detail, String instancePath) {
		ProblemDetail problem = ProblemDetail.forStatus(code.status());
		problem.setTitle(code.status().getReasonPhrase());
		problem.setDetail(detail != null ? detail : code.defaultDetail());
		if (instancePath != null) {
			problem.setInstance(URI.create(instancePath));
		}
		problem.setProperty(CODE_PROPERTY, code.name());
		return problem;
	}
}
