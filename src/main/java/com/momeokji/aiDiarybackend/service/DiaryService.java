package com.momeokji.aiDiarybackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

	private final DiaryRepository diaryRepository;
	private final UserRepository userRepository;

	private static final String OPENAI_API_URL = "https://api.openai.com/v1/responses";

	@Value("${openai.secret-key}")
	private String apiKey;




}
