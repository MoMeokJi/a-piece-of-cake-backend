package com.momeokji.aiDiarybackend.common.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class KstTimeTest {

	@Test
	void toOffsetAttachesPlusNine() {
		OffsetDateTime out = KstTime.toOffset(LocalDateTime.of(2025, 9, 16, 21, 12));

		assertEquals("2025-09-16T21:12+09:00", out.toString());
	}

	@Test
	void nullStaysNull() {
		assertNull(KstTime.toOffset(null));
	}
}
