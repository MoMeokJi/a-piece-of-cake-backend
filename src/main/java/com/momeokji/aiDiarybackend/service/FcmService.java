package com.momeokji.aiDiarybackend.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.momeokji.aiDiarybackend.entity.MemberDevice;
import com.momeokji.aiDiarybackend.repository.MemberDeviceRepository;
import com.momeokji.aiDiarybackend.service.push.FcmSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 푸시 발송(스펙 8.3). 회원 단위가 아니라 기기 단위로 보낸다 — 한 회원의 {@code fcm_token} 이 있는 모든 기기.
 * FCM 이 토큰 무효({@code UNREGISTERED}, {@code INVALID_ARGUMENT})라고 하면 그 기기의 토큰을 지운다.
 * 한 기기의 실패가 다른 기기 발송을 막지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

	private final MemberDeviceRepository memberDeviceRepository;
	private final FcmSender fcmSender;

	/** 특정 회원의 모든 푸시 가능 기기로 발송한다. */
	public void sendToMember(String memberId, String title, String body, Map<String, String> data) {
		List<MemberDevice> devices = memberDeviceRepository.findByMemberIdAndFcmTokenIsNotNull(memberId);
		if (devices.isEmpty()) {
			log.info("푸시 가능한 기기가 없어 FCM 을 건너뜁니다. memberId={}", memberId);
			return;
		}
		for (MemberDevice device : devices) {
			sendToDevice(device, title, body, data);
		}
	}

	/** 관리자 공지: 푸시 가능한 모든 기기로 발송한다. */
	public void sendNotificationToAllMembers(String title, String body) {
		List<MemberDevice> devices = memberDeviceRepository.findByFcmTokenIsNotNull();
		Map<String, String> data = Map.of("type", "ADMIN_MESSAGE");

		int successCount = 0;
		int failureCount = 0;
		for (MemberDevice device : devices) {
			if (sendToDevice(device, title, body, data)) {
				successCount++;
			} else {
				failureCount++;
			}
		}
		log.info("FCM 전체 발송 완료. total={}, success={}, failure={}", devices.size(), successCount, failureCount);
	}

	public void remindNotification(String memberId) {
		String title = "오늘 하루는 어땠나요?";
		String body = "지금 기록하지 않으면 사라질지도 몰라요!";
		sendToMember(memberId, title, body, Map.of("type", "REMIND"));
	}

	public void feedbackNotification(String memberId, Long diaryId) {
		String title = "오늘의 시식평이 도착했어요 🍰";
		String body = "달콤한 한마디가 기다리고 있어요, 지금 확인해보세요.";
		sendToMember(memberId, title, body, Map.of("type", "FEEDBACK", "diaryId", String.valueOf(diaryId)));
	}

	/** @return 성공 여부. 실패는 로그로 남기고 토큰 무효면 정리한다. */
	private boolean sendToDevice(MemberDevice device, String title, String body, Map<String, String> data) {
		String token = device.getFcmToken();
		try {
			String response = fcmSender.send(buildMessage(token, device.getOs(), title, body, data));
			log.info("FCM 발송 완료. memberId={}, deviceId={}, response={}", device.getMemberId(), device.getDeviceId(), response);
			return true;
		} catch (FirebaseMessagingException e) {
			if (isTokenInvalid(e.getMessagingErrorCode())) {
				log.warn("FCM 토큰이 무효라 정리합니다. memberId={}, deviceId={}, code={}",
					device.getMemberId(), device.getDeviceId(), e.getMessagingErrorCode());
				memberDeviceRepository.clearFcmToken(token);
			} else {
				log.error("FCM 발송 실패. memberId={}, deviceId={}, code={}",
					device.getMemberId(), device.getDeviceId(), e.getMessagingErrorCode(), e);
			}
			return false;
		} catch (RuntimeException e) {
			log.error("FCM 발송 실패. memberId={}, deviceId={}", device.getMemberId(), device.getDeviceId(), e);
			return false;
		}
	}

	private static boolean isTokenInvalid(MessagingErrorCode code) {
		return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
	}

	/** OS 별 메시지 구성은 기존과 같다. Android 는 데이터 메시지(HIGH), iOS 는 notification + APNs 설정. */
	private static Message buildMessage(String token, String os, String title, String body, Map<String, String> data) {
		Map<String, String> payload = new HashMap<>();
		if (data != null) {
			payload.putAll(data);
		}
		payload.put("title", title);
		payload.put("body", body);

		Message.Builder builder = Message.builder()
			.setToken(token)
			.putAllData(payload);

		if ("AND".equalsIgnoreCase(os)) {
			builder.setAndroidConfig(AndroidConfig.builder()
				.setPriority(AndroidConfig.Priority.HIGH)
				.build());
		} else if ("IOS".equalsIgnoreCase(os)) {
			builder
				.setNotification(Notification.builder()
					.setTitle(title)
					.setBody(body)
					.build())
				.setApnsConfig(ApnsConfig.builder()
					.putHeader("apns-priority", "5")
					.putHeader("apns-push-type", "alert")
					.setAps(Aps.builder()
						.setContentAvailable(true)
						.setBadge(0)
						.setSound("true")
						.build())
					.build());
		}
		return builder.build();
	}
}
