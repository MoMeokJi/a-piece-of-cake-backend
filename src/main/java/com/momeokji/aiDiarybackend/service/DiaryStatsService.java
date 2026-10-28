package com.momeokji.aiDiarybackend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.dto.response.DiaryStatsResponseDto;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;

/**
 * 마이페이지 통계와 일일 작성 제한. 날짜 경계는 전부 KST {@link Clock} 으로 구한다.
 * 소프트 삭제된 일기는 어느 숫자에도 들어가지 않는다.
 */
@Service
public class DiaryStatsService {

	private final DiaryRepository diaryRepository;
	private final Clock clock;
	private final int dailyLimit;

	public DiaryStatsService(DiaryRepository diaryRepository, Clock clock,
		@Value("${diary.daily-limit:3}") int dailyLimit) {
		this.diaryRepository = diaryRepository;
		this.clock = clock;
		this.dailyLimit = dailyLimit;
	}

	@Transactional(readOnly = true)
	public DiaryStatsResponseDto getStats(String memberId) {
		LocalDate today = LocalDate.now(clock);
		LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
		LocalDateTime nextMonthStart = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

		List<LocalDate> datesDesc = diaryRepository.findDistinctDiaryDates(memberId).stream()
			.map(DiaryStatsService::toLocalDate)
			.toList();

		return DiaryStatsResponseDto.builder()
			.totalCount(diaryRepository.countByUserIdAndIsValidTrue(memberId))
			.monthCount(countBetween(memberId, monthStart, nextMonthStart))
			.streakDays(streakDays(datesDesc, today))
			.todayCount(countToday(memberId, today))
			.dailyLimit(dailyLimit)
			.build();
	}

	/** 오늘(KST) 유효한 일기가 제한 이상이면 429. 생성·확정 경로가 OpenAI 를 부르기 전에 호출한다. */
	@Transactional(readOnly = true)
	public void assertUnderDailyLimit(String memberId) {
		if (countToday(memberId, LocalDate.now(clock)) >= dailyLimit) {
			throw new BusinessException(ErrorCode.DIARY_DAILY_LIMIT_EXCEEDED);
		}
	}

	/**
	 * 연속 기록 일수. 오늘 기록이 있으면 오늘부터, 없으면 어제부터 거슬러 올라가며 하루라도 비면 멈춘다.
	 *
	 * @param datesDesc 기록이 있는 날짜(중복 없음, 최신순)
	 */
	static int streakDays(List<LocalDate> datesDesc, LocalDate today) {
		Set<LocalDate> dates = new HashSet<>(datesDesc);
		LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
		int streak = 0;
		while (dates.contains(cursor)) {
			streak++;
			cursor = cursor.minusDays(1);
		}
		return streak;
	}

	private long countToday(String memberId, LocalDate today) {
		return countBetween(memberId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
	}

	private long countBetween(String memberId, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
		return diaryRepository.countByUserIdAndIsValidTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
			memberId, fromInclusive, toExclusive);
	}

	/** 네이티브 {@code DATE()} 결과는 드라이버에 따라 {@code java.sql.Date} 또는 {@code LocalDate} 로 온다. */
	private static LocalDate toLocalDate(Object value) {
		if (value instanceof LocalDate date) {
			return date;
		}
		if (value instanceof java.sql.Date date) {
			return date.toLocalDate();
		}
		return LocalDate.parse(value.toString());
	}
}
