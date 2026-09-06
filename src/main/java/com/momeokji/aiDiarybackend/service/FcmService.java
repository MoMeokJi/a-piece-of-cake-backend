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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {
    private final MemberRepository memberRepository;

    //특정 회원에게 날리는 기능
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

        if ("AND".equalsIgnoreCase(os)) {
            AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build();
            msgBuilder.setAndroidConfig(androidConfig);
        }
        else if ("IOS".equalsIgnoreCase(os)) {
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

    //모든 멤버에게 fcm을 날리는 기능
    public void sendNotificationToAllMembers(
        String title,
        String body
    ) {
        List<Member> members = memberRepository.findByIsValidTrue();

        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        Map<String, String> data = Map.of(
            "type", "ADMIN_MESSAGE"
        );

        for (Member member : members) {
            if (!hasFcmToken(member)) {
                skippedCount++;
                log.info(
                    "토큰에러로 인해 FCM메시지가 전송되지 않았습니다. memberId={}",
                    member.getMemberId()
                );
                continue;
            }

            try {
                sendMessage(member, title, body, data);
                successCount++;
            } catch (FirebaseMessagingException | RuntimeException e) {
                failureCount++;

                log.error(
                    "FCM알림 에러. memberId={}, error={}",
                    member.getMemberId(),
                    e.getMessage(),
                    e
                );
            }
        }

        log.info(
            "FCM이 정상적으로 전송되었습니다. total={}, success={}, failure={}, skipped={}",
            members.size(),
            successCount,
            failureCount,
            skippedCount
        );

        return;
    }

    // fcm메시지 생성
    private void sendMessage(Member member, String title, String body, Map<String, String> data) throws FirebaseMessagingException {

        Map<String, String> payload = new HashMap<>();

        if (data != null) {
            payload.putAll(data);
        }

        payload.put("title", title);
        payload.put("body", body);

        Message.Builder messageBuilder = Message.builder()
            .setToken(member.getDeviceId())
            .putAllData(payload);

        String mobileOS = member.getMobileOS();

        if ("AND".equalsIgnoreCase(mobileOS)) {
            AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build();

            messageBuilder.setAndroidConfig(androidConfig);

        } else if ("IOS".equalsIgnoreCase(mobileOS)) {
            messageBuilder
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .setApnsConfig(
                    ApnsConfig.builder()
                        .putHeader("apns-priority", "5")
                        .putHeader("apns-push-type", "alert")
                        .setAps(
                            Aps.builder()
                                .setContentAvailable(true)
                                .setBadge(0)
                                .setSound("true")
                                .build()
                        )
                        .build()
                );
        }

        Message message = messageBuilder.build();

        String firebaseResponse =
            FirebaseMessaging.getInstance().send(message);

        log.info(
            "FCM message sent. memberId={}, firebaseResponse={}",
            member.getMemberId(),
            firebaseResponse
        );
    }


    private boolean hasFcmToken(Member member) {
        return member.getDeviceId() != null
            && !member.getDeviceId().isBlank();
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