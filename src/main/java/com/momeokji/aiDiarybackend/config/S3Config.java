package com.momeokji.aiDiarybackend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@RequiredArgsConstructor
public class S3Config {

	private final S3Properties props;

	@Bean
	public S3Client s3Client() {
		AwsCredentialsProvider creds =
			(props.accessKey() != null && !props.accessKey().isBlank()
				&& props.secretAccessKey() != null && !props.secretAccessKey().isBlank())
				? StaticCredentialsProvider.create(
				AwsBasicCredentials.create(props.accessKey(), props.secretAccessKey()))
				: DefaultCredentialsProvider.create(); // 로컬/EC2 등 기본자격

		return S3Client.builder()
			.region(Region.of(props.region()))
			.credentialsProvider(creds)
			.build();
	}
}
