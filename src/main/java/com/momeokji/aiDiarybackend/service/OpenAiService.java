package com.momeokji.aiDiarybackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.config.OpenAiConfig;
import com.momeokji.aiDiarybackend.dto.openai.OpenAiRequestDto;
import com.momeokji.aiDiarybackend.dto.openai.OpenAiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

	private final OpenAiConfig aiConfig;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public String call(String promptKey, Object inputObj) {
		try {
			OpenAiConfig.Prompt prompt = aiConfig.prompts().get(promptKey);
			if (prompt == null) {
				throw new IllegalArgumentException("설정되지 않은 prompt key입니다.");
			}

			String inputJson = objectMapper.writeValueAsString(inputObj);

			OpenAiRequestDto body = OpenAiRequestDto.builder()
				.prompt(OpenAiRequestDto.Prompt.builder()
					.id(prompt.id())
					.version(prompt.version())
					.build())
				.input(inputJson)
				.build();

			WebClient.Builder builder = WebClient.builder()
				.baseUrl(aiConfig.apiUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiConfig.secretKey());
			if (aiConfig.beta() != null && !aiConfig.beta().isBlank()) {
				builder.defaultHeader("OpenAI-Beta", aiConfig.beta());
			}
			WebClient client = builder.build();

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
