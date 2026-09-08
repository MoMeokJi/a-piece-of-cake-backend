package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.request.FcmMessageRequestDto;
import com.momeokji.aiDiarybackend.service.FcmService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FcmController {

	private final FcmService fcmService;

	@PostMapping("/fcmMessages")
	public ResponseEntity<Void> sendFcmMessage(
		@Valid @RequestBody FcmMessageRequestDto request
	) {
		fcmService.sendNotificationToAllMembers(
			request.getTitle(),
			request.getBody()
		);

		return ResponseEntity.ok().build();
	}
}