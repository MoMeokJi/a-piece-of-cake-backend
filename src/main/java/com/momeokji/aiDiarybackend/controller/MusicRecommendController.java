package com.momeokji.aiDiarybackend.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.dto.music.MusicResponseDto;
import com.momeokji.aiDiarybackend.dto.request.DiaryMoodRequestDto;
import com.momeokji.aiDiarybackend.service.MusicService;
import com.momeokji.aiDiarybackend.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/recommend")
public class MusicRecommendController {
    private final OpenAiService openAiService;
    private final MusicService musicService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    public ResponseEntity<MusicResponseDto> recommendMusic(@RequestBody DiaryMoodRequestDto diary){
                try{
                    String resultJson = openAiService.call("mood", diary.getContent());
                    String firstJson = resultJson.split("}\\s*\\{")[0] + "}";   //Json이 가끔 두개씩 들어와서 앞Json만 받기.
                    JsonNode node = objectMapper.readTree(firstJson);
                    String mood = node.get("mood").asText();

            MusicResponseDto music = musicService.getRandomMusic(mood);

            return ResponseEntity.ok(music);
        } catch (Exception e){
            throw new RuntimeException("음악 추천 오류");
        }
    }

}

