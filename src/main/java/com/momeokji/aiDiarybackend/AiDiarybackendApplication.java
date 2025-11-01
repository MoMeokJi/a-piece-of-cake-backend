package com.momeokji.aiDiarybackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiDiarybackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiDiarybackendApplication.class, args);
	}

}
