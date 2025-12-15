package com.momeokji.aiDiarybackend.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.entity.Music;
import com.momeokji.aiDiarybackend.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class MusicService {

    private final MusicRepository musicRepository;
    private final OpenAiService openAiService;
    private final ObjectMapper om = new ObjectMapper();
    private final Random random = new Random();

    public Music pickRandomMusicByDiaryText(String diaryText) {
        try {
            String result = openAiService.call("music", Map.of("text", diaryText));

            // 모델이 가끔 JSON 2개를 붙여주는 케이스 방어
            String firstJson = result.split("}\\s*\\{")[0] + "}";
            JsonNode node = om.readTree(firstJson);
            String mood = node.get("mood").asText(); // 예: "happy", "calm" 등

            List<Music> list = musicRepository.findByMood(mood);

            //맞는 mood에 곡이 없을 시
            if (list.isEmpty()) {
                return null;
            }

            return list.get(random.nextInt(list.size()));
        } catch (Exception e) {
            // 실패 시 음악을 null로 반환하도록
            return null;
        }
    }
}
