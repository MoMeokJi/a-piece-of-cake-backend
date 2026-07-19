package com.momeokji.aiDiarybackend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.response.DailyDiaryCountResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DailySignUpCountResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DashboardMainResponseDto;
import com.momeokji.aiDiarybackend.service.DashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/dashboards")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;


	@GetMapping("/main")
	public ResponseEntity<DashboardMainResponseDto> getMainStatistics() {
		DashboardMainResponseDto response =
			dashboardService.getMainStatistics();

		return ResponseEntity.ok(response);
	}


	@GetMapping("/recentuser")
	public ResponseEntity<List<DailySignUpCountResponseDto>>
	getRecentUserStatistics() {

		List<DailySignUpCountResponseDto> response =
			dashboardService.getRecentUserStatistics();

		return ResponseEntity.ok(response);
	}

	@GetMapping("/recentdiary")
	public ResponseEntity<List<DailyDiaryCountResponseDto>>
	getRecentDiaryStatistics() {

		List<DailyDiaryCountResponseDto> response =
			dashboardService.getRecentDiaryStatistics();

		return ResponseEntity.ok(response);
	}
}