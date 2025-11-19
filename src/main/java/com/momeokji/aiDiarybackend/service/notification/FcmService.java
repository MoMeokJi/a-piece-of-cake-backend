package com.momeokji.aiDiarybackend.service.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FcmService {

    private MemberRepository memberRepository;

    public void sendFcmNotification(String memberId, String title,
                                    String body, Map<String, String> data) throws FirebaseMessagingException {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("member not found"));

        String token = member.getFcmToken();
        if (token == null || token.isBlank()) {
            return;

        }
        Notification notification = Notification.builder() //Firebase 제공 class, 알림 내용
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder() // Firebase 제공 클래스, 대상, 알림 내용, 추가데이터(key-value)
                .setToken(token)
                .setNotification(notification)
                .putAllData(data)
                .build();

        FirebaseMessaging.getInstance().send(message);
    }

    public void remindNotification(String memberId)
            throws FirebaseMessagingException{
        String title = "일기 독려 알림";
        String body = "일기 빨리 써라";

        Map<String, String> data = Map.of("type", "REMIND");

        sendFcmNotification(memberId, title, body, data);
    }

    public void feedbackNotification(String memberId, Long diaryId)
        throws FirebaseMessagingException {
        String title="공감 알림";
        String body="공감스페이스";

        Map<String, String> data = Map.of("type", "FEEDBACK",
                "diaryId", String.valueOf(diaryId));
        sendFcmNotification(memberId, title, body, data);
    }



}
