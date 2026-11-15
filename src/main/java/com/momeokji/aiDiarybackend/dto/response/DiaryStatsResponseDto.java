package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;

/** {@code GET /users/me/stats}. 모두 KST 기준이며 삭제된 일기는 세지 않는다. */
@Getter
@Builder
public class DiaryStatsResponseDto {
	/** 모은 조각 총 개수 */
	private long totalCount;
	/** 이번 달 개수 */
	private long monthCount;
	/** 연속 기록 일수. 오늘 기록이 없으면 어제까지의 연속을 센다. */
	private int streakDays;
	/** 오늘 쓴 개수 */
	private long todayCount;
	/** 하루 작성 제한. 앱이 상수를 복제하지 않도록 같이 준다. */
	private int dailyLimit;
}
