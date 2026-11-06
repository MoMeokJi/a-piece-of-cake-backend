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
			if (res.getOutput() != null && !res.getOutput().isEmpty()
				&& res.getOutput().get(0).getContent() != null) {
				return res.getOutput().get(0).getContent().stream()
					.map(OpenAiResponseDto.ContentItem::getText)
					.filter(t -> t != null && !t.isBlank())
					.map(String::trim)
					.findFirst()
					.orElseThrow(() -> new RuntimeException("응답 결과가 없습니다."));
			}
			throw new RuntimeException("output text가 없습니다.");
		} catch (WebClientResponseException e) {
			log.error("OpenAI error. status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
			throw e;
		} catch (Exception e) {
			log.error("OpenAI 호출 실패", e);
			throw new RuntimeException(e);
		}
	}
}
