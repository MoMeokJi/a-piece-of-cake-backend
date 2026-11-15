package com.momeokji.aiDiarybackend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.DiaryColor;
import com.momeokji.aiDiarybackend.entity.DiaryImage;
import com.momeokji.aiDiarybackend.repository.DiaryColorRepository;
import com.momeokji.aiDiarybackend.repository.DiaryImageRepository;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

/**
 * 삭제 표시된 일기·색상·사진을 실제로 지우는 스케줄러.
 *
 * <p>개인정보처리방침이 "탈퇴·삭제 후 4일 이내 영구 삭제"를 약속한다. 그 숫자는 유예 3일 + 실행 주기 1일에서 나온다.
 * 주기나 유예를 바꾸면 {@code site/privacy.html} 4항도 함께 고쳐야 한다.
 */
class DiaryDeleteSchedulerIntegrationTest extends IntegrationTestSupport {

	static final String BUCKET_URL = "https://piece-of-cake-image.s3.ap-northeast-2.amazonaws.com/";

	@MockitoBean
	S3Client s3Client;

	@Autowired
	DiaryDeleteScheduler scheduler;

	@Autowired
	DiaryRepository diaryRepository;

	@Autowired
	DiaryImageRepository diaryImageRepository;

	@Autowired
	DiaryColorRepository diaryColorRepository;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void purgesRowsDeletedThreeDaysAgoAndKeepsNewerAndAliveOnes() {
		LocalDateTime now = LocalDateTime.now();
		Diary expired = insertDiary("m1", now.minusDays(3).minusMinutes(1));
		Diary recent = insertDiary("m1", now.minusDays(3).plusHours(1));
		Diary alive = insertDiary("m1", null);
		for (Diary d : new Diary[] {expired, recent, alive}) {
			insertImage(d, 0, "m1/diary/" + d.getDiaryId() + "/a.jpg");
			insertColor(d);
		}

		scheduler.cleanupSoftDeletedDiaries();

		assertThat(diaryRepository.findAll()).extracting(Diary::getDiaryId)
			.containsExactlyInAnyOrder(recent.getDiaryId(), alive.getDiaryId());
		assertThat(diaryImageRepository.findAll()).extracting(DiaryImage::getDiaryId)
			.containsExactlyInAnyOrder(recent.getDiaryId(), alive.getDiaryId());
		assertThat(diaryColorRepository.findAll()).extracting(DiaryColor::getDiaryId)
			.containsExactlyInAnyOrder(recent.getDiaryId(), alive.getDiaryId());
		verify(s3Client).deleteObject(keyIs("m1/diary/" + expired.getDiaryId() + "/a.jpg"));
		verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
	}

	/** S3 삭제가 실패한 사진은 행을 남겨 둬야 한다. 행을 지우면 파일 주소를 잃어 다시 지울 수 없다. */
	@Test
	void keepsImageRowWhenS3DeleteFailsAndRetriesOnNextRun() {
		Diary diary = insertDiary("m1", LocalDateTime.now().minusDays(4));
		String failingKey = "m1/diary/" + diary.getDiaryId() + "/fail.jpg";
		String okKey = "m1/diary/" + diary.getDiaryId() + "/ok.jpg";
		insertImage(diary, 0, failingKey);
		insertImage(diary, 1, okKey);
		insertColor(diary);
		doThrow(SdkClientException.create("S3 장애")).when(s3Client).deleteObject(keyIs(failingKey));

		scheduler.cleanupSoftDeletedDiaries();

		assertThat(diaryImageRepository.findAll()).extracting(DiaryImage::getImageUrl)
			.containsExactly(BUCKET_URL + failingKey);
		assertThat(diaryRepository.findAll()).isEmpty();
		assertThat(diaryColorRepository.findAll()).isEmpty();

		doReturn(DeleteObjectResponse.builder().build()).when(s3Client).deleteObject(keyIs(failingKey));

		scheduler.cleanupSoftDeletedDiaries();

		assertThat(diaryImageRepository.findAll()).isEmpty();
		verify(s3Client, times(2)).deleteObject(keyIs(failingKey));
		verify(s3Client, times(1)).deleteObject(keyIs(okKey));
	}

	/** 탈퇴한 회원의 일기·사진·색상은 유예 기간 동안 남았다가 그 뒤 실행에서 사라진다. */
	@Test
	void withdrawnMembersDiaryDataIsPurgedAfterGracePeriod() throws Exception {
		String token = loginAsMember("sub-purge");
		String memberId = objectMapper.readTree(mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andReturn().getResponse().getContentAsString()).get("memberId").asText();
		Diary diary = insertDiary(memberId, null);
		String key = memberId + "/diary/" + diary.getDiaryId() + "/a.jpg";
		insertImage(diary, 0, key);
		insertColor(diary);

		mockMvc.perform(delete("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNoContent());
		scheduler.cleanupSoftDeletedDiaries();

		assertThat(diaryRepository.findAll()).hasSize(1);
		assertThat(diaryImageRepository.findAll()).hasSize(1);
		assertThat(diaryColorRepository.findAll()).hasSize(1);
		verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

		// 유예 3일이 지난 상태로 만든다. 시각을 직접 넣지 않고 DB 안에서 3일을 빼 시간대 변환을 피한다.
		for (String table : new String[] {"diary", "diary_image", "diary_color"}) {
			jdbc.update("update " + table + " set deleted_at = TIMESTAMPADD(DAY, -3, deleted_at)");
		}
		scheduler.cleanupSoftDeletedDiaries();

		assertThat(diaryRepository.findAll()).isEmpty();
		assertThat(diaryImageRepository.findAll()).isEmpty();
		assertThat(diaryColorRepository.findAll()).isEmpty();
		verify(s3Client).deleteObject(keyIs(key));
	}

	/** 방침의 "4일 이내"는 매일 실행을 전제로 한다. 실행 간격이 하루를 넘으면 그 약속이 깨진다. */
	@Test
	void runsEveryDayAtTwoAmKst() throws Exception {
		Scheduled scheduled = DiaryDeleteScheduler.class.getMethod("cleanupSoftDeletedDiaries").getAnnotation(Scheduled.class);
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
		CronExpression cron = CronExpression.parse(scheduled.cron());

		LocalDateTime previous = cron.next(LocalDateTime.of(2026, 1, 1, 0, 0));
		for (int i = 0; i < 800; i++) {
			LocalDateTime next = cron.next(previous);
			assertThat(Duration.between(previous, next)).as("%s 다음 실행", previous).isEqualTo(Duration.ofDays(1));
			assertThat(next.getHour()).isEqualTo(2);
			previous = next;
		}
	}

	private Diary insertDiary(String memberId, LocalDateTime deletedAt) {
		return diaryRepository.save(Diary.builder()
			.userId(memberId)
			.createdAt(LocalDateTime.now().minusDays(10))
			.content("본문")
			.isValid(deletedAt == null)
			.deletedAt(deletedAt)
			.build());
	}

	private void insertImage(Diary diary, int imageId, String key) {
		diaryImageRepository.save(DiaryImage.builder()
			.diaryId(diary.getDiaryId())
			.imageId(imageId)
			.imageUrl(BUCKET_URL + key)
			.isValid(diary.getIsValid())
			.deletedAt(diary.getDeletedAt())
			.build());
	}

	private void insertColor(Diary diary) {
		diaryColorRepository.save(DiaryColor.builder()
			.diaryId(diary.getDiaryId())
			.colorId(0)
			.colorName("#C3E9A6")
			.isValid(diary.getIsValid())
			.deletedAt(diary.getDeletedAt())
			.build());
	}

	private static DeleteObjectRequest keyIs(String key) {
		return argThat(request -> request != null && key.equals(request.key()));
	}
}
