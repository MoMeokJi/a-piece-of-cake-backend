package com.momeokji.aiDiarybackend.controller;


import com.momeokji.aiDiarybackend.dto.request.FcmRequestDto;
import com.momeokji.aiDiarybackend.service.DiaryService;
import com.momeokji.aiDiarybackend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/fcm")
public class FcmController {

    private final NotificationService notificationService;

    @PostMapping("/token")
    public ResponseEntity<Void> updateFcmToken(@RequestBody FcmRequestDto dto){
        notificationService.updateFcmToken(dto.getMemberId(), dto.getFcmToken());
        return ResponseEntity.ok().build();
    }

}
