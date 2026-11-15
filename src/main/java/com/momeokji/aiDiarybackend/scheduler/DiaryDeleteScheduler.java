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

	/** 삭제 표시 후 실제로 지우기까지 두는 유예. */
	static final int GRACE_DAYS = 3;

	/**
	 * 삭제 표시된 지 {@link #GRACE_DAYS}일이 지난 일기·색상·사진을 실제로 지운다. 매일 새벽 2시(KST)에 실행한다.
	 *
	 * <p>개인정보처리방침 4항의 "4일 이내 영구 삭제"는 유예 3일 + 실행 주기 1일에서 나온 숫자다.
	 * 주기나 유예를 바꾸면 {@code site/privacy.html} 도 함께 고친다.
	 *
	 * <p>사진은 S3 에서 지워진 것만 행을 지운다. S3 삭제가 실패한 사진은 삭제 표시 상태로 남아 다음 실행에서 다시 시도한다.
	 * S3 는 이미 없는 파일의 삭제도 성공으로 답하므로 재시도가 겹쳐도 안전하다.
	 */
	@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
	@Transactional
	public void cleanupSoftDeletedDiaries() {

		LocalDateTime threshold = LocalDateTime.now().minusDays(GRACE_DAYS);

		List<DiaryImage> expiredImages = diaryImageRepository.findExpiredImages(threshold);
		List<DiaryImage> removedFromS3 = diaryImageService.deleteImagesFromS3(expiredImages);
		diaryImageRepository.deleteAll(removedFromS3);

		int colorDeleted = diaryColorRepository.deleteExpired(threshold);
		int diaryDeleted = diaryRepository.deleteExpired(threshold);

		log.info(
			"Diary 삭제 완료 - images={}, colors={}, diaries={}",
			removedFromS3.size(), colorDeleted, diaryDeleted
		);

		int failed = expiredImages.size() - removedFromS3.size();
		if (failed > 0) {
			log.error("S3 사진 삭제 실패 {}건. 행을 남겨 두었고 다음 실행에서 다시 시도합니다.", failed);
		}
	}
}
