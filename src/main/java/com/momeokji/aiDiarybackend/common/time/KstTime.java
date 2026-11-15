package com.momeokji.aiDiarybackend.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 서비스의 유일한 타임존. 앱은 한국 사용자 전용이라 날짜 경계(오늘, 이번 달, 연속 일수)는 전부 KST 로 센다.
 *
 * <p>DB 의 {@code DATETIME} 컬럼은 KST 벽시계 시각이다. 응답으로 내려줄 때는 오프셋을 붙여 앱이 오해하지 않게 한다.
 */
public final class KstTime {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private KstTime() {
	}

	/** DB 의 KST 벽시계 시각에 {@code +09:00} 을 붙인다. null 이면 null. */
	public static OffsetDateTime toOffset(LocalDateTime kstWallClock) {
		return kstWallClock == null ? null : kstWallClock.atZone(ZONE).toOffsetDateTime();
	}
}
