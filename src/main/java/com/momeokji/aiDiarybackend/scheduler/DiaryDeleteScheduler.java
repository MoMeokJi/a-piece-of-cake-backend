package com.momeokji.aiDiarybackend.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.entity.DiaryImage;
import com.momeokji.aiDiarybackend.repository.DiaryColorRepository;
import com.momeokji.aiDiarybackend.repository.DiaryImageRepository;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.service.DiaryImageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiaryDeleteScheduler {

	private final DiaryRepository diaryRepository;
	private final DiaryImageRepository diaryImageRepository;
	private final DiaryColorRepository diaryColorRepository;
	private final DiaryImageService diaryImageService;

	/**
	 * 3일에 한 번 실행 (새벽 2시)
	 */
	@Scheduled(cron = "0 0 2 */3 * *", zone = "Asia/Seoul")
	@Transactional
	public void cleanupSoftDeletedDiaries() {

		LocalDateTime threshold = LocalDateTime.now().minusDays(3);

		List<DiaryImage> expiredImages = diaryImageRepository.findExpiredImages(threshold);

		diaryImageService.deleteImagesFromS3(expiredImages);

		int imageDeleted = diaryImageRepository.deleteExpired(threshold);
		int colorDeleted = diaryColorRepository.deleteExpired(threshold);
		int diaryDeleted = diaryRepository.deleteExpired(threshold);

		log.info(
			"Diary 삭제 완료 - images={}, colors={}, diaries={}",
			imageDeleted, colorDeleted, diaryDeleted
		);
	}
}
