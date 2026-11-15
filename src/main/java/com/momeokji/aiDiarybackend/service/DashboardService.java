package com.momeokji.aiDiarybackend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.dto.response.DailyDiaryCountResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DailySignUpCountResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DashboardMainResponseDto;
import com.momeokji.aiDiarybackend.repository.DailyCountStatistics;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.repository.MusicRepository;
import com.momeokji.aiDiarybackend.repository.QuestionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

	private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Seoul");
	private static final int RECENT_DAYS = 30;

	private final MemberRepository memberRepository;
	private final DiaryRepository diaryRepository;
	private final QuestionRepository questionRepository;
	private final MusicRepository musicRepository;

	//soft delete 안된 애들만
	public DashboardMainResponseDto getMainStatistics() {
		return DashboardMainResponseDto.builder()
			.userCount(memberRepository.countByIsValidTrue())
			.diaryCount(diaryRepository.countByIsValidTrue())
			.questionCount(questionRepository.count())
			.musicCount(musicRepository.count())
			.build();
	}

	//가입자 없으면 0명으로
	public List<DailySignUpCountResponseDto> getRecentUserStatistics() {
		DateRange dateRange = createRecentDateRange();

		Map<LocalDate, Long> dailyCountMap = toDailyCountMap(
			memberRepository.countDailySignUps(
				dateRange.from(),
				dateRange.to()
			)
		);

		return IntStream.range(0, RECENT_DAYS)
			.mapToObj(dateRange.startDate()::plusDays)
			.map(date -> DailySignUpCountResponseDto.builder()
				.date(date)
				.dailySignUpCount(
					dailyCountMap.getOrDefault(date, 0L)
				)
				.build()
			)
			.toList();
	}

	//일기 없으면 0건으로
	public List<DailyDiaryCountResponseDto> getRecentDiaryStatistics() {
		DateRange dateRange = createRecentDateRange();

		Map<LocalDate, Long> dailyCountMap = toDailyCountMap(
			diaryRepository.countDailyDiaries(
				dateRange.from(),
				dateRange.to()
			)
		);

		return IntStream.range(0, RECENT_DAYS)
			.mapToObj(dateRange.startDate()::plusDays)
			.map(date -> DailyDiaryCountResponseDto.builder()
				.date(date)
				.dailyDiaryCount(
					dailyCountMap.getOrDefault(date, 0L)
				)
				.build()
			)
			.toList();
	}


	private Map<LocalDate, Long> toDailyCountMap(
		List<DailyCountStatistics> statistics
	) {
		return statistics.stream()
			.collect(Collectors.toMap(
				DailyCountStatistics::getDate,
				statistic -> statistic.getTotalCount() == null
					? 0L
					: statistic.getTotalCount()
			));
	}


	private DateRange createRecentDateRange() {
		LocalDate today = LocalDate.now(DASHBOARD_ZONE);
		LocalDate startDate = today.minusDays(RECENT_DAYS - 1L);

		LocalDateTime from = startDate.atStartOfDay();
		LocalDateTime to = today.plusDays(1).atStartOfDay();

		return new DateRange(startDate, from, to);
	}

	private record DateRange(
		LocalDate startDate,
		LocalDateTime from,
		LocalDateTime to
	) {
	}
}