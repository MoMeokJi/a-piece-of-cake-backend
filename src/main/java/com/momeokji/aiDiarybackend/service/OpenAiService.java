package com.momeokji.aiDiarybackend.service;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.config.OpenAiConfig;
import com.momeokji.aiDiarybackend.dto.openai.OpenAiRequestDto;
import com.momeokji.aiDiarybackend.dto.openai.OpenAiResponseDto;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * OpenAI Responses API 호출. 프롬프트는 저장소의 System 메시지({@link OpenAiPromptLoader})와
 * 입력 객체의 JSON 문자열을 user 메시지로 보낸다. 대시보드 저장 프롬프트는 쓰지 않는다.
 */
@Slf4j
@Service
public class OpenAiService {

	private final OpenAiConfig aiConfig;
	private final OpenAiPromptLoader prompts;
	private final WebClient client;
	/** 입력 객체 직렬화는 스프링 ObjectMapper 설정과 무관하게 기본 설정으로 고정한다(프롬프트가 이 형식을 전제로 쓰였다). */
	private final ObjectMapper objectMapper = new ObjectMapper();

	public OpenAiService(OpenAiConfig aiConfig, OpenAiPromptLoader prompts, WebClient.Builder webClientBuilder) {
		this.aiConfig = aiConfig;
		this.prompts = prompts;
		WebClient.Builder builder = webClientBuilder
			.baseUrl(aiConfig.apiUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiConfig.secretKey());
		if (aiConfig.beta() != null && !aiConfig.beta().isBlank()) {
			builder.defaultHeader("OpenAI-Beta", aiConfig.beta());
		}
		this.client = builder.build();
	}

	public String call(String promptKey, Object inputObj) {
		try {
			OpenAiConfig.Prompt prompt = aiConfig.prompts().get(promptKey);
			if (prompt == null) {
				throw new IllegalArgumentException("설정되지 않은 prompt key입니다: " + promptKey);
			}

			String inputJson = objectMapper.writeValueAsString(inputObj);

			OpenAiRequestDto body = OpenAiRequestDto.builder()
				.model(prompt.model())
				.input(List.of(
					OpenAiRequestDto.Message.system(prompts.system(promptKey)),
					OpenAiRequestDto.Message.user(inputJson)))
				.temperature(prompt.temperature())
				.maxOutputTokens(prompt.maxOutputTokens())
				.reasoning(prompt.reasoningEffort() == null ? null
					: new OpenAiRequestDto.Reasoning(prompt.reasoningEffort()))
				.build();

			OpenAiResponseDto res = client.post()
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(Mono.just(body), OpenAiRequestDto.class)
				.retrieve()
				.bodyToMono(OpenAiResponseDto.class)
				.block();

			if (res == null) {
				throw new RuntimeException("OpenAI 응답이 없습니다.");
			}

			if (res.getOutput_text() != null && !res.getOutput_text().isBlank()) {
				return res.getOutput_text().trim();
			}
			String text = joinOutputText(res);
			if (text.isBlank()) {
				throw new RuntimeException("OpenAI 응답에 본문이 없습니다. " + describe(res));
			}
			return text.trim();
		} catch (WebClientResponseException e) {
			log.error("OpenAI error. status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
			throw e;
		} catch (Exception e) {
			log.error("OpenAI 호출 실패", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * {@code output} 의 모든 항목에서 {@code output_text} 조각만 순서대로 이어 붙인다(SDK 의 {@code output_text} 와 같은 규칙).
	 * reasoning 을 켠 모델은 본문 없는 reasoning 항목을 message 앞에 두므로 첫 항목만 보면 안 된다.
	 */
	private static String joinOutputText(OpenAiResponseDto res) {
		if (res.getOutput() == null) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		for (OpenAiResponseDto.OutputItem item : res.getOutput()) {
			if (item == null || item.getContent() == null) {
				continue;
			}
			for (OpenAiResponseDto.ContentItem part : item.getContent()) {
				if (part != null && part.getText() != null && "output_text".equals(part.getType())) {
					text.append(part.getText());
				}
			}
		}
		return text.toString();
	}

	/** 본문이 없을 때 원인을 로그에서 바로 알 수 있도록 상태와 항목 종류를 적는다. 본문 내용은 넣지 않는다. */
	private static String describe(OpenAiResponseDto res) {
		String reason = res.getIncomplete_details() == null ? null : res.getIncomplete_details().getReason();
		List<String> types = res.getOutput() == null ? List.of()
			: res.getOutput().stream().map(item -> item == null ? "null" : String.valueOf(item.getType())).toList();
		return "status=" + res.getStatus() + ", incomplete_reason=" + reason + ", output_types=" + types;
	}
}
