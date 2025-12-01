package com.momeokji.aiDiarybackend.config;

import java.io.IOException;
import java.io.InputStream;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
public class FirebaseConfig {

	@Value("${firebase.credentials.path}")
	private String credentialsPath;

	// 없어도 되는 값이라 : 로 기본값, 추후 필요시 변경
	@Value("${firebase.project-id:}")
	private String projectId;

	@PostConstruct
	public void init() throws IOException {

		if (!FirebaseApp.getApps().isEmpty()) {
			log.info("FirebaseApp 초기화가 이미 완료 된 상태입니다.");
			return;
		}

		log.info("Initializing FirebaseApp with credentials: {}", credentialsPath);

		ClassPathResource resource = new ClassPathResource(
			credentialsPath.replace("classpath:", "")
		);

		try (InputStream serviceAccount = resource.getInputStream()) {
			FirebaseOptions.Builder builder = FirebaseOptions.builder()
				.setCredentials(GoogleCredentials.fromStream(serviceAccount));

			//projectId null일 경우 무시하도록 처리
			if (projectId != null && !projectId.isBlank()) {
				builder.setProjectId(projectId);
			}

			FirebaseOptions options = builder.build();
			FirebaseApp.initializeApp(options);

			log.info("FirebaseApp 초기화 성공");
		}
	}
}
