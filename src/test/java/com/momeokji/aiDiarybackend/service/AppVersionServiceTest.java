package com.momeokji.aiDiarybackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.momeokji.aiDiarybackend.common.time.KstTime;
import com.momeokji.aiDiarybackend.dto.request.AppVersionUpdateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.AppVersionResponseDto;
import com.momeokji.aiDiarybackend.entity.AppVersion;
import com.momeokji.aiDiarybackend.repository.AppVersionRepository;

/** 같은 플랫폼의 최초 등록이 동시에 두 번 오면 PK 충돌이 나는데, 상대가 만든 행을 갱신해 흡수한다. */
class AppVersionServiceTest {

	AppVersionRepository repository = mock(AppVersionRepository.class);
	Clock clock = Clock.fixed(Instant.parse("2026-10-29T03:00:00Z"), KstTime.ZONE);
	AppVersionService service = new AppVersionService(repository, clock,
		new TransactionTemplate(mock(PlatformTransactionManager.class)));

	@Test
	void concurrentFirstInsertFallsBackToUpdatingTheWinnersRow() {
		AppVersion winner = AppVersion.create("IOS", "1.0.0", null, null, LocalDateTime.now(clock));
		when(repository.findById("IOS")).thenReturn(Optional.empty(), Optional.of(winner));
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

		AppVersionResponseDto out = service.upsert("IOS", new AppVersionUpdateRequestDto("1.2.0", "1.3.0", null));

		assertEquals("1.2.0", out.getMinVersion());
		assertEquals("1.2.0", winner.getMinVersion());
		verify(repository).saveAndFlush(any());
	}
}
