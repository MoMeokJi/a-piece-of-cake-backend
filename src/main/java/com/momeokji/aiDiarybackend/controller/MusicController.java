package com.momeokji.aiDiarybackend.controller;

import com.momeokji.aiDiarybackend.dto.request.MusicCreateRequest;
import com.momeokji.aiDiarybackend.dto.request.MusicUpdateRequest;
import com.momeokji.aiDiarybackend.dto.response.MusicResponse;
import com.momeokji.aiDiarybackend.service.MusicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/musics")
@RequiredArgsConstructor
public class MusicController {

	private final MusicService musicService;

	@PostMapping
	public ResponseEntity<MusicResponse> createMusic(
		@Valid @RequestBody MusicCreateRequest request
	) {
		MusicResponse response =
			musicService.createMusic(request);

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(response);
	}

	@PatchMapping("/{musicId}")
	public ResponseEntity<MusicResponse> updateMusic(
		@PathVariable Long musicId,
		@RequestBody MusicUpdateRequest request
	) {
		MusicResponse response =
			musicService.updateMusic(musicId, request);

		return ResponseEntity.ok(response);
	}

	@DeleteMapping("/{musicId}")
	public ResponseEntity<Void> deleteMusic(
		@PathVariable Long musicId
	) {
		musicService.deleteMusic(musicId);

		return ResponseEntity.noContent().build();
	}


	@GetMapping
	public ResponseEntity<List<MusicResponse>> getMusicsByMood(
		@RequestParam String mood
	) {
		List<MusicResponse> response =
			musicService.getMusicsByMood(mood);

		return ResponseEntity.ok(response);
	}

	@GetMapping("/all")
	public ResponseEntity<List<MusicResponse>> getAllMusics() {
		List<MusicResponse> response =
			musicService.getAllMusics();

		return ResponseEntity.ok(response);
	}
}