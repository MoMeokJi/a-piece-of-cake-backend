package com.momeokji.aiDiarybackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.MemberDevice;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberDeviceRepository;
import com.momeokji.aiDiarybackend.service.push.FcmSender;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FcmServiceTest {

	static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

	@Autowired
	MemberDeviceRepository repo;

	@Autowired
	DiaryRepository diaryRepository;

	@Autowired
	TestEntityManager em;

	FcmSender sender;
	FcmService service;

	@BeforeEach
	void setUp() {
		sender = mock(FcmSender.class);
		service = new FcmService(repo, sender);
	}

	@Test
	void sendsToEveryDeviceOfMember() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");
		device("dev-b", "m1", "IOS", "fcm-B");
		device("dev-c", "m2", "AND", "fcm-C");

		service.sendToMember("m1", "t", "b", Map.of("type", "X"));

		ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
		verify(sender, times(2)).send(captor.capture());
		assertThat(captor.getAllValues()).extracting(FcmServiceTest::tokenOf).containsExactlyInAnyOrder("fcm-A", "fcm-B");
	}

	@Test
	void skipsDevicesWithoutFcmToken() throws Exception {
		device("dev-a", "m1", "AND", null);

		service.sendToMember("m1", "t", "b", null);

		verify(sender, never()).send(any());
	}

	@Test
	void unregisteredTokenIsCleared() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");
		device("dev-b", "m1", "AND", "fcm-B");
		FirebaseMessagingException unregistered = mock(FirebaseMessagingException.class);
		when(unregistered.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
		when(sender.send(any())).thenAnswer(inv -> {
			if ("fcm-A".equals(tokenOf(inv.getArgument(0)))) {
				throw unregistered;
			}
			return "ok";
		});

		service.sendToMember("m1", "t", "b", null);

		verify(sender, times(2)).send(any());
		em.clear(); // 벌크 UPDATE 는 영속성 컨텍스트를 비우지 않으므로 DB 상태를 다시 읽는다
		assertThat(repo.findById("dev-a").orElseThrow().getFcmToken()).isNull();
		assertThat(repo.findById("dev-b").orElseThrow().getFcmToken()).isEqualTo("fcm-B");
	}

	@Test
	void clearingInvalidTokenDoesNotDetachOtherEntitiesInSameTransaction() throws Exception {
		// DiarySympathyScheduler 는 한 트랜잭션에서 일기를 순회하며 feedbackMsg 를 더티체킹으로 저장하고 푸시를 보낸다.
		// 중간에 무효 토큰 정리가 일어나도 그 뒤 일기의 변경이 커밋되어야 한다.
		device("dev-a", "m1", "AND", "fcm-A");
		Diary first = diaryRepository.save(Diary.builder().userId("m1").content("1").createdAt(NOW).build());
		Diary second = diaryRepository.save(Diary.builder().userId("m1").content("2").createdAt(NOW).build());
		em.flush();
		FirebaseMessagingException unregistered = mock(FirebaseMessagingException.class);
		when(unregistered.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
		when(sender.send(any())).thenThrow(unregistered);

		first.updateFeedbackMsg("공감 1");
		service.feedbackNotification("m1", first.getDiaryId());   // 여기서 fcm-A 정리
		second.updateFeedbackMsg("공감 2");
		em.flush();
		em.clear();

		assertThat(diaryRepository.findById(first.getDiaryId()).orElseThrow().getFeedbackMsg()).isEqualTo("공감 1");
		assertThat(diaryRepository.findById(second.getDiaryId()).orElseThrow().getFeedbackMsg()).isEqualTo("공감 2");
		assertThat(repo.findById("dev-a").orElseThrow().getFcmToken()).isNull();
	}

	@Test
	void invalidArgumentTokenIsCleared() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");
		FirebaseMessagingException invalid = mock(FirebaseMessagingException.class);
		when(invalid.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
		when(sender.send(any())).thenThrow(invalid);

		service.sendToMember("m1", "t", "b", null);

		em.clear();
		assertThat(repo.findById("dev-a").orElseThrow().getFcmToken()).isNull();
	}

	@Test
	void otherFailureKeepsToken() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");
		FirebaseMessagingException internal = mock(FirebaseMessagingException.class);
		when(internal.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
		when(sender.send(any())).thenThrow(internal);

		service.sendToMember("m1", "t", "b", null);

		assertThat(repo.findById("dev-a").orElseThrow().getFcmToken()).isEqualTo("fcm-A");
	}

	@Test
	void runtimeFailureDoesNotPropagateAndKeepsToken() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");
		when(sender.send(any())).thenThrow(new IllegalStateException("firebase not initialized"));

		service.sendToMember("m1", "t", "b", null);

		assertThat(repo.findById("dev-a").orElseThrow().getFcmToken()).isEqualTo("fcm-A");
	}

	@Test
	void iosDeviceGetsApnsAlertWithImmediatePriorityAndDefaultSound() throws Exception {
		device("dev-a", "m1", "IOS", "fcm-A");

		service.sendToMember("m1", "t", "b", null);

		Message message = sent();
		assertThat(field(message, "androidConfig")).isNull();
		assertNotificationIs(message, "t", "b");

		ApnsConfig apns = (ApnsConfig)field(message, "apnsConfig");
		assertThat(apns).isNotNull();
		Map<String, String> headers = mapField(apns, "headers");
		assertThat(headers).containsEntry("apns-priority", "10").containsEntry("apns-push-type", "alert");
		Map<String, Object> payload = mapField(apns, "payload");
		@SuppressWarnings("unchecked")
		Map<String, Object> aps = (Map<String, Object>)payload.get("aps");
		assertThat(aps).containsEntry("badge", 0).containsEntry("sound", "default");
		// 앱이 백그라운드에서 할 일이 없으므로 content-available 은 넣지 않는다
		assertThat(aps).doesNotContainKey("content-available");
	}

	@Test
	void androidDeviceGetsNotificationOnHighImportanceChannel() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");

		service.sendToMember("m1", "t", "b", null);

		Message message = sent();
		assertThat(field(message, "apnsConfig")).isNull();
		assertNotificationIs(message, "t", "b");

		AndroidConfig android = (AndroidConfig)field(message, "androidConfig");
		assertThat(android).isNotNull();
		assertThat(field(android, "priority")).isEqualTo("high");
		Object notification = field(android, "notification");
		assertThat(notification).isNotNull();
		assertThat(field(notification, "channelId")).isEqualTo("high_importance_channel");
	}

	@Test
	void dataCarriesTypeAndTitleBodyRegardlessOfOs() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");
		device("dev-b", "m1", "IOS", "fcm-B");

		service.feedbackNotification("m1", 7L);

		ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
		verify(sender, times(2)).send(captor.capture());
		for (Message message : captor.getAllValues()) {
			Map<String, String> data = mapField(message, "data");
			assertThat(data)
				.containsEntry("type", "FEEDBACK")
				.containsEntry("diaryId", "7")
				.containsKeys("title", "body");
		}
	}

	@Test
	void remindDataHasNoDiaryId() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");

		service.remindNotification("m1");

		Map<String, String> data = mapField(sent(), "data");
		assertThat(data).containsEntry("type", "REMIND").doesNotContainKey("diaryId");
	}

	@Test
	void broadcastHitsAllPushableDevices() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");
		device("dev-b", "m2", "IOS", "fcm-B");
		device("dev-c", "m3", "IOS", null);

		service.sendNotificationToAllMembers("t", "b");

		ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
		verify(sender, times(2)).send(captor.capture());
		assertThat(captor.getAllValues()).extracting(FcmServiceTest::tokenOf).containsExactlyInAnyOrder("fcm-A", "fcm-B");
	}

	@Test
	void remindAndFeedbackDoNotDeclareCheckedExceptions() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");

		service.remindNotification("m1");
		service.feedbackNotification("m1", 7L);

		verify(sender, times(2)).send(any());
	}

	private void device(String deviceId, String memberId, String os, String fcm) {
		repo.save(MemberDevice.open(deviceId, memberId, os, fcm, "hash-" + deviceId, NOW.plusDays(60), NOW));
		em.flush();
	}

	private Message sent() throws FirebaseMessagingException {
		ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
		verify(sender).send(captor.capture());
		return captor.getValue();
	}

	private static void assertNotificationIs(Message message, String title, String body) {
		Object notification = field(message, "notification");
		assertThat(notification).isNotNull();
		assertThat(field(notification, "title")).isEqualTo(title);
		assertThat(field(notification, "body")).isEqualTo(body);
	}

	@SuppressWarnings("unchecked")
	static <V> Map<String, V> mapField(Object target, String name) {
		return (Map<String, V>) field(target, name);
	}

	static String tokenOf(Message message) {
		return (String) field(message, "token");
	}

	static Object field(Object target, String name) {
		try {
			Field f = target.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return f.get(target);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

}
