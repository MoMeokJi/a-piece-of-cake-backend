package com.momeokji.aiDiarybackend.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberDeviceRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.service.FcmService;

class DiaryRemindSchedulerTest {

	MemberRepository memberRepository = mock(MemberRepository.class);
	MemberDeviceRepository memberDeviceRepository = mock(MemberDeviceRepository.class);
	DiaryRepository diaryRepository = mock(DiaryRepository.class);
	FcmService fcmService = mock(FcmService.class);

	DiaryRemindScheduler scheduler = new DiaryRemindScheduler(memberRepository, memberDeviceRepository, diaryRepository, fcmService);

	@Test
	void sendsOnlyToMembersInPushableSetUsingOneQuery() {
		when(memberRepository.findByIsValidTrueAndNotificationEnabledTrue()).thenReturn(List.of(Member.create("m1"), Member.create("m2"), Member.create("m3")));
		when(memberDeviceRepository.findMemberIdsWithFcmToken()).thenReturn(Set.of("m1", "m3"));
		LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
		for (String id : List.of("m1", "m2", "m3")) {
			when(diaryRepository.findTop1ByUserIdAndIsValidTrueOrderByCreatedAtDesc(id))
				.thenReturn(Optional.of(Diary.builder().userId(id).createdAt(threeDaysAgo).build()));
		}

		scheduler.sendRemindNotifications();

		verify(fcmService).remindNotification("m1");
		verify(fcmService).remindNotification("m3");
		verify(fcmService, never()).remindNotification("m2");
		verify(memberDeviceRepository, times(1)).findMemberIdsWithFcmToken();
		verify(diaryRepository, never()).findTop1ByUserIdAndIsValidTrueOrderByCreatedAtDesc("m2");
	}

	@Test
	void memberWithoutDiaryIsSkipped() {
		when(memberRepository.findByIsValidTrueAndNotificationEnabledTrue()).thenReturn(List.of(Member.create("m1")));
		when(memberDeviceRepository.findMemberIdsWithFcmToken()).thenReturn(Set.of("m1"));
		when(diaryRepository.findTop1ByUserIdAndIsValidTrueOrderByCreatedAtDesc("m1")).thenReturn(Optional.empty());

		scheduler.sendRemindNotifications();

		verify(fcmService, never()).remindNotification(any());
	}

	/** 대상 조회는 알림을 켠 회원만 돌려주는 쿼리 하나로 한다. 전체 회원 조회는 쓰지 않는다. */
	@Test
	void remindSkipsMembersWithNotificationOff() {
		when(memberRepository.findByIsValidTrueAndNotificationEnabledTrue()).thenReturn(List.of(Member.create("on")));
		when(memberDeviceRepository.findMemberIdsWithFcmToken()).thenReturn(Set.of("on", "off"));
		when(diaryRepository.findTop1ByUserIdAndIsValidTrueOrderByCreatedAtDesc(any()))
			.thenReturn(Optional.of(Diary.builder().userId("on").createdAt(LocalDateTime.now().minusDays(2)).build()));

		scheduler.sendRemindNotifications();

		verify(fcmService).remindNotification("on");
		verify(fcmService, never()).remindNotification("off");
		verify(memberRepository, never()).findByIsValidTrue();
	}
}
