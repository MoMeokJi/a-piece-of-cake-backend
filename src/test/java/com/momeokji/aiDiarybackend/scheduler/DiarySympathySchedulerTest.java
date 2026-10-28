package com.momeokji.aiDiarybackend.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.service.FcmService;
import com.momeokji.aiDiarybackend.service.OpenAiService;

/** 공감(시식평) 메시지는 항상 만들되, 푸시는 알림을 켠 회원에게만 보낸다. */
class DiarySympathySchedulerTest {

	DiaryRepository diaryRepository = mock(DiaryRepository.class);
	OpenAiService openAiService = mock(OpenAiService.class);
	FcmService fcmService = mock(FcmService.class);
	MemberRepository memberRepository = mock(MemberRepository.class);

	DiarySympathyScheduler scheduler = new DiarySympathyScheduler(diaryRepository, openAiService, fcmService, memberRepository);

	@Test
	void sympathySavesMessageButSkipsPushWhenOff() {
		Diary diary = Diary.builder().diaryId(1L).userId("m1").content("본문").build();
		when(diaryRepository.findTop100ByIsValidTrueAndFeedbackMsgIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(any()))
			.thenReturn(List.of(diary));
		when(openAiService.call(eq("sympathy"), any())).thenReturn("따뜻한 한마디");
		when(memberRepository.findByMemberIdInAndIsValidTrue(Set.of("m1"))).thenReturn(List.of(Member.create("m1")));

		scheduler.fillFeedbackMessages();

		assertEquals("따뜻한 한마디", diary.getFeedbackMsg());
		verify(fcmService, never()).feedbackNotification(any(), any());
	}

	@Test
	void sympathyPushesWhenOn() {
		Diary diary = Diary.builder().diaryId(1L).userId("m1").content("본문").build();
		when(diaryRepository.findTop100ByIsValidTrueAndFeedbackMsgIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(any()))
			.thenReturn(List.of(diary));
		when(openAiService.call(eq("sympathy"), any())).thenReturn("따뜻한 한마디");
		Member member = Member.create("m1");
		member.changeNotificationEnabled(true);
		when(memberRepository.findByMemberIdInAndIsValidTrue(Set.of("m1"))).thenReturn(List.of(member));

		scheduler.fillFeedbackMessages();

		verify(fcmService).feedbackNotification("m1", 1L);
	}

	/** 일기마다 회원을 조회하지 않고, 대상 일기의 회원을 한 번에 읽는다. */
	@Test
	void loadsMembersInOneQuery() {
		Diary d1 = Diary.builder().diaryId(1L).userId("m1").content("a").build();
		Diary d2 = Diary.builder().diaryId(2L).userId("m2").content("b").build();
		Diary d3 = Diary.builder().diaryId(3L).userId("m1").content("c").build();
		when(diaryRepository.findTop100ByIsValidTrueAndFeedbackMsgIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(any()))
			.thenReturn(List.of(d1, d2, d3));
		when(openAiService.call(eq("sympathy"), any())).thenReturn("한마디");
		Member m1 = Member.create("m1");
		m1.changeNotificationEnabled(true);
		when(memberRepository.findByMemberIdInAndIsValidTrue(Set.of("m1", "m2"))).thenReturn(List.of(m1, Member.create("m2")));

		scheduler.fillFeedbackMessages();

		verify(memberRepository, times(1)).findByMemberIdInAndIsValidTrue(Set.of("m1", "m2"));
		verify(memberRepository, never()).findByMemberIdAndIsValidTrue(any());
		verify(fcmService).feedbackNotification("m1", 1L);
		verify(fcmService).feedbackNotification("m1", 3L);
		verify(fcmService, never()).feedbackNotification(eq("m2"), any());
	}
}
