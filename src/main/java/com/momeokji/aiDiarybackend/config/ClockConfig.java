package com.momeokji.aiDiarybackend.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.momeokji.aiDiarybackend.common.time.KstTime;

/** 토큰 발급·만료와 날짜 경계(오늘, 이번 달) 계산에 쓰는 KST 시계. 테스트에서 고정/가변 시계로 바꿔 끼운다. */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.system(KstTime.ZONE);
	}
}
