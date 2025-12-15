package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.momeokji.aiDiarybackend.service.FcmService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/debug/fcm")
public class FcmController {

	private final FcmService fcmService;

	@PostMapping("/remind/{memberId}")
	public ResponseEntity<String> testRemind(@PathVariable String memberId) throws FirebaseMessagingException {
		fcmService.remindNotification(memberId);
		return ResponseEntity.ok("remind sent (check logs)");
	}

	@PostMapping("/feedback/{memberId}/{diaryId}")
	public ResponseEntity<String> testFeedback(
		@PathVariable String memberId,
		@PathVariable Long diaryId) throws FirebaseMessagingException {
		fcmService.feedbackNotification(memberId, diaryId);
		return ResponseEntity.ok("feedback sent (check logs)");
	}
}

