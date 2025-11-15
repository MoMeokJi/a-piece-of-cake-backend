package com.momeokji.aiDiarybackend.service.notification;


import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService { //fcm token 저장

    private final MemberRepository memberRepository;

    @Transactional
    public void updateFcmToken(String memberId, String fcmToken) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("member not found"));
        member.updateFcmToken(fcmToken);

    }
}
