package com.momeokji.aiDiarybackend.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FcmRequestDto {
    private String memberId;
    private String fcmToken;
}
