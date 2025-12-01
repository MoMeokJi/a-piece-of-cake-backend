package com.momeokji.aiDiarybackend.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
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

        Notification notification = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build();

        Message.Builder msgBuilder = Message.builder()
            .setToken(token)
            .setNotification(notification)
            .putAllData(payload);

        String os = member.getMobileOS();


        if (os.equals("AND"))
        {
            AndroidNotification androidNotification = AndroidNotification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

            AndroidConfig androidConfig = AndroidConfig.builder()
                .setNotification(androidNotification)
                .setPriority(AndroidConfig.Priority.HIGH)
                .build();

            msgBuilder.setAndroidConfig(androidConfig);
        }
        else if (os.equals("IOS"))
        {
            ApnsConfig apnsConfig = ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .putHeader("apns-push-type", "alert")
                .setAps(Aps.builder()
                    .setContentAvailable(true)      //백 그라운드 처리
                    .setAlert(ApsAlert.builder().setTitle(title).setBody(body).build())
                    .build())
                .build();

            msgBuilder.setApnsConfig(apnsConfig);
        }

        Message message = msgBuilder.build();

        String response = FirebaseMessaging.getInstance().send(message);
        log.info("fcm message memberId = {}, response = {}",memberId, response);

    }

    public void remindNotification(String memberId)
            throws FirebaseMessagingException{
        String title = "조각 케이크에서 메시지가 왔어요";
        String body = "일기를 안쓴지 2일이나 지났어요 지금 바로 쓰러가볼까요?";

        Map<String, String> data = Map.of("type", "REMIND");
        sendFcmNotification(memberId, title, body, data);
    }

    public void feedbackNotification(String memberId, Long diaryId)
        throws FirebaseMessagingException {

        String title="조각 케이크에서 메시지가 왔어요";
        String body="공감 알림이 도착했어요 지금 바로 확인해보세요!";

        Map<String, String> data = Map.of("type", "FEEDBACK",
                "diaryId", String.valueOf(diaryId));
        sendFcmNotification(memberId, title, body, data);
    }



}
