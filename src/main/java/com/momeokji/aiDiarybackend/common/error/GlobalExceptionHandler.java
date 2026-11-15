package com.momeokji.aiDiarybackend.common.error;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 전역 예외 → RFC 9457 Problem Details 변환.
 *
 * <ul>
 *   <li>{@link BusinessException}: 코드의 상태와 메시지 그대로</li>
 *   <li>{@link IllegalArgumentException}: 400 {@code BAD_REQUEST}, 메시지 유지(기존 API 호환)</li>
 *   <li>Spring MVC 표준 예외(검증, 본문 파싱 등): 부모 클래스가 만든 ProblemDetail 에 {@code code} 추가</li>
 *   <li>그 외: 500 {@code INTERNAL_ERROR}, 내부 정보는 숨기고 로그에만 남김</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ProblemDetail> handleBusiness(BusinessException e, HttpServletRequest request) {
		return respond(ProblemDetailFactory.create(e.getErrorCode(), e.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
		return respond(ProblemDetailFactory.create(ErrorCode.BAD_REQUEST, e.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) throws Exception {
		// Security 예외는 ExceptionTranslationFilter 가 entry point / access denied handler 로 처리하도록 되돌린다.
		if (e instanceof AuthenticationException || e instanceof AccessDeniedException) {
			throw e;
		}
		log.error("처리되지 않은 예외. uri={}", request.getRequestURI(), e);
		return respond(ProblemDetailFactory.create(ErrorCode.INTERNAL_ERROR, null, request.getRequestURI()));
	}

	/** Spring MVC 표준 예외의 ProblemDetail 바디에 {@code code} 를 붙인다. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
		HttpStatusCode statusCode, WebRequest request) {

		ProblemDetail problem;
		if (body instanceof ProblemDetail pd) {
			problem = pd;
		} else if (ex instanceof ErrorResponse er) {
			problem = er.getBody();
		} else {
			problem = ProblemDetail.forStatus(statusCode);
		}

		HttpStatus status = HttpStatus.resolve(statusCode.value());
		String code = statusCode.value() == HttpStatus.BAD_REQUEST.value()
			? ErrorCode.VALIDATION_FAILED.name()
			: status != null ? status.name() : String.valueOf(statusCode.value());

		if (status != null) {
			problem.setTitle(status.getReasonPhrase());
			if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
				problem.setDetail(ErrorCode.VALIDATION_FAILED.defaultDetail());
			}
		}
		if (request instanceof ServletWebRequest swr && problem.getInstance() == null) {
			problem.setInstance(URI.create(swr.getRequest().getRequestURI()));
		}
		problem.setProperty(ProblemDetailFactory.CODE_PROPERTY, code);

		HttpHeaders out = headers != null ? new HttpHeaders(headers) : new HttpHeaders();
		out.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		return new ResponseEntity<>(problem, out, statusCode);
	}

	private ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		if (problem.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
			// RFC 6750: 401 에는 WWW-Authenticate 를 붙인다(스펙 9.1). 여기서 나는 401 은 refresh 토큰 문제 등
			// Bearer 토큰 자체의 문제가 아닐 수 있으므로 error 파라미터 없이 scheme 만 알린다.
			headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		}
		return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
	}
}
