package com.momeokji.aiDiarybackend;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.momeokji.aiDiarybackend.common.time.KstTime;
import com.momeokji.aiDiarybackend.config.OpenAiConfig;
import com.momeokji.aiDiarybackend.config.S3Properties;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = { OpenAiConfig.class, S3Properties.class })
public class AiDiarybackendApplication {

	static {
		// 운영 서버 JVM 이 UTC 여도 @CreationTimestamp 와 LocalDateTime.now() 가 KST 벽시계 시각을 쓰게 한다.
		TimeZone.setDefault(TimeZone.getTimeZone(KstTime.ZONE));
	}

	public static void main(String[] args) {
		SpringApplication.run(AiDiarybackendApplication.class, args);
	}

}
