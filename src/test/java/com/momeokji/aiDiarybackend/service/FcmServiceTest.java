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

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.momeokji.aiDiarybackend.entity.MemberDevice;
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
		assertThat(repo.findById("dev-a").orElseThrow().getFcmToken()).isNull();
		assertThat(repo.findById("dev-b").orElseThrow().getFcmToken()).isEqualTo("fcm-B");
	}

	@Test
	void invalidArgumentTokenIsCleared() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");
		FirebaseMessagingException invalid = mock(FirebaseMessagingException.class);
		when(invalid.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
		when(sender.send(any())).thenThrow(invalid);

		service.sendToMember("m1", "t", "b", null);

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
	void iosDeviceGetsApnsNotification() throws Exception {
		device("dev-a", "m1", "IOS", "fcm-A");

		service.sendToMember("m1", "t", "b", null);

		ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
		verify(sender).send(captor.capture());
		assertThat(field(captor.getValue(), "apnsConfig")).isNotNull();
		assertThat(field(captor.getValue(), "notification")).isNotNull();
		assertThat(field(captor.getValue(), "androidConfig")).isNull();
	}

	@Test
	void androidDeviceGetsHighPriorityDataMessage() throws Exception {
		device("dev-a", "m1", "AND", "fcm-A");

		service.sendToMember("m1", "t", "b", null);

		ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
		verify(sender).send(captor.capture());
		assertThat(field(captor.getValue(), "androidConfig")).isNotNull();
		assertThat(field(captor.getValue(), "apnsConfig")).isNull();
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
