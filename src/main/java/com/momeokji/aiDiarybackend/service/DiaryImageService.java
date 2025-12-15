package com.momeokji.aiDiarybackend.service;

import com.momeokji.aiDiarybackend.config.S3Properties;
import com.momeokji.aiDiarybackend.entity.DiaryImage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import net.coobird.thumbnailator.Thumbnails;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryImageService {

	private final S3Client s3;
	private final S3Properties props;

	//multipartFile list 형식으로 이미지 받고 압축,업로드 후 s3 url list로 반환
	public List<String> uploadMany(String userId, Long diaryId, List<MultipartFile> files) {
		List<String> urls = new ArrayList<>();
		if (files == null || files.isEmpty()){
			return urls;
		}
		for (MultipartFile f : files) {
			urls.add(compressAndUpload(userId, diaryId, f));
		}
		return urls;
	}

	//단일 파일 압축 & S3업로드
	public String compressAndUpload(String userId, Long diaryId, MultipartFile file) {
		try (InputStream in = file.getInputStream();
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

			// 간단 포맷 판별 (없으면 jpeg로)
			String contentType = file.getContentType();
			String format = (contentType != null && contentType.toLowerCase().contains("png")) ? "png" : "jpg";

			// 긴 변 1600px로 리사이즈(가로세로 비율 유지). jpg면 품질 0.8
			Thumbnails.Builder<? extends InputStream> builder = Thumbnails.of(in).size(1600, 1600).outputFormat(format);
			if (!"png".equals(format)) builder.outputQuality(0.8f);
			builder.toOutputStream(out);

			byte[] bytes = out.toByteArray();

			String objectKey = "%s/diary/%d/%s.%s".formatted(
				userId, diaryId, UUID.randomUUID(), format);

			PutObjectRequest put = PutObjectRequest.builder()
				.bucket(props.bucket())
				.key(objectKey)
				.contentType(contentType != null ? contentType : ("image/" + format))
				.build();

			s3.putObject(put, RequestBody.fromBytes(bytes));

			// 정적 퍼블릭 버킷이라면 아래 URL, 프라이빗이면 presigned URL 사용
			return "https://%s.s3.%s.amazonaws.com/%s".formatted(
				props.bucket(), props.region(), objectKey);

		} catch (Exception e) {
			throw new RuntimeException("이미지 업로드 실패", e);
		}
	}

	public void deleteImagesFromS3(List<DiaryImage> images) {
		for (DiaryImage image : images) {
			deleteSingleImage(image.getImageUrl());
		}
	}

	private void deleteSingleImage(String imageUrl) {
		try {
			String objectKey = extractObjectKey(imageUrl);

			DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
				.bucket(props.bucket())
				.key(objectKey)
				.build();

			s3.deleteObject(deleteRequest);

			log.info("S3 이미지 삭제 완료 key={}", objectKey);

		} catch (Exception e) {
			log.error("S3 이미지 삭제 실패 url={}", imageUrl, e);
		}
	}

	private String extractObjectKey(String imageUrl) {
		URI uri = URI.create(imageUrl);
		String path = uri.getPath(); // /userId/diary/1/xxx.jpg
		return path.startsWith("/") ? path.substring(1) : path;
	}

}
