package com.momeokji.aiDiarybackend.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {
    private final MemberRepository memberRepository;

    public void sendFcmNotification(String memberId, String title,
        String body, Map<String, String> data) throws FirebaseMessagingException {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalStateException("member not found"));
        String token = member.getDeviceId();
        if (token == null || token.isBlank()) {
            return;
        }

        Map<String, String> payload = new HashMap<>();
        if (data != null) {
            payload.putAll(data);
        }
        payload.put("title", title);
        payload.put("body", body);

        Message.Builder msgBuilder = Message.builder()
            .setToken(token)
            .putAllData(payload);

        String os = member.getMobileOS();

        if (os.equals("AND")) {
            AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build();
            msgBuilder.setAndroidConfig(androidConfig);
        }
        else if (os.equals("IOS")) {
            // ✅ 변경: setNotification 방식으로 리팩토링
            msgBuilder
                .setNotification(Notification.builder()  // ✅ 추가: 범용 Notification 설정
                    .setTitle(title)
                    .setBody(body)
                    .build())
                .setApnsConfig(ApnsConfig.builder()
                    .putHeader("apns-priority", "5")
                    .putHeader("apns-push-type", "alert")
                    .setAps(Aps.builder()
                        .setContentAvailable(true)  // 백그라운드 처리
                        .setBadge(0)
                        .setSound("true")
                        .build())
                    .build());
        }

        Message message = msgBuilder.build();
        String response = FirebaseMessaging.getInstance().send(message);
        log.info("fcm message memberId = {}, response = {}", memberId, response);
    }

    public void remindNotification(String memberId)
        throws FirebaseMessagingException {
        String title = "오늘 하루는 어땠나요?";
        String body = "지금 기록하지 않으면 사라질지도 몰라요!";
        Map<String, String> data = Map.of("type", "REMIND");
        sendFcmNotification(memberId, title, body, data);
    }

    public void feedbackNotification(String memberId, Long diaryId)
        throws FirebaseMessagingException {
        String title = "오늘의 시식평이 도착했어요 \uD83C\uDF70";
        String body = "달콤한 한마디가 기다리고 있어요, 지금 확인해보세요.";
        Map<String, String> data = Map.of("type", "FEEDBACK",
            "diaryId", String.valueOf(diaryId));
        sendFcmNotification(memberId, title, body, data);
    }
}