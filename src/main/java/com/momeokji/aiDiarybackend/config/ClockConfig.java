package com.momeokji.aiDiarybackend.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 토큰 발급·만료 계산에 쓰는 시계. 테스트에서 고정/가변 시계로 바꿔 끼운다. */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
