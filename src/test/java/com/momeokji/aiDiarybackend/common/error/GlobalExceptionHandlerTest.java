package com.momeokji.aiDiarybackend.common.error;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void businessExceptionBecomesProblemDetailWithCode() throws Exception {
		mockMvc.perform(get("/throw/business"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.title").value("Not Found"))
			.andExpect(jsonPath("$.instance").value("/throw/business"));
	}

	@Test
	void illegalArgumentBecomesBadRequestKeepingMessage() throws Exception {
		mockMvc.perform(get("/throw/illegal"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.detail").value("text는 필수입니다."));
	}

	@Test
	void beanValidationFailureIsValidationFailed() throws Exception {
		mockMvc.perform(post("/throw/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void unreadableBodyIsValidationFailed() throws Exception {
		mockMvc.perform(post("/throw/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void unexpectedExceptionHidesInternals() throws Exception {
		mockMvc.perform(get("/throw/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.detail").value(not(containsString("password"))));
	}

	@Test
	void unauthorizedBusinessExceptionCarriesWwwAuthenticate() throws Exception {
		mockMvc.perform(post("/throw/refresh-invalid"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_INVALID"))
			// 서버가 토큰 문제의 종류를 알려 줄 수 없으니(error 파라미터 없음) scheme 만 알린다.
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
				.header().string("WWW-Authenticate", "Bearer"));
	}

	@RestController
	static class ThrowingController {

		@GetMapping("/throw/business")
		void business() {
			throw new BusinessException(ErrorCode.DEVICE_NOT_FOUND);
		}

		@GetMapping("/throw/illegal")
		void illegal() {
			throw new IllegalArgumentException("text는 필수입니다.");
		}

		@PostMapping("/throw/validate")
		void validate(@Valid @RequestBody Body body) {
		}

		@GetMapping("/throw/unexpected")
		void unexpected() {
			throw new RuntimeException("db password=xxx");
		}

		@PostMapping("/throw/refresh-invalid")
		void refreshInvalid() {
			throw new BusinessException(ErrorCode.REFRESH_INVALID);
		}
	}

	@Getter
	static class Body {
		@NotBlank
		private String text;
	}
}
