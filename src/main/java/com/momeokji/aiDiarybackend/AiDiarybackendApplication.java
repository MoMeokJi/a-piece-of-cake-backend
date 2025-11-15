package com.momeokji.aiDiarybackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.momeokji.aiDiarybackend.config.OpenAiConfig;
import com.momeokji.aiDiarybackend.config.S3Properties;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = { OpenAiConfig.class, S3Properties.class })
public class AiDiarybackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiDiarybackendApplication.class, args);
	}

}
