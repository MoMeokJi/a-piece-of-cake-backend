package com.momeokji.aiDiarybackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.dto.request.MusicCreateRequest;
import com.momeokji.aiDiarybackend.dto.request.MusicUpdateRequest;
import com.momeokji.aiDiarybackend.dto.response.MusicResponse;
import com.momeokji.aiDiarybackend.entity.Music;
import com.momeokji.aiDiarybackend.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MusicService {

    private final MusicRepository musicRepository;
    private final OpenAiService openAiService;

    private final ObjectMapper om = new ObjectMapper();
    private final Random random = new Random();

    /**
     * 일기 내용을 분석해 mood에 맞는 음악 중 하나를 무작위로 반환한다.
     */
    public Music pickRandomMusicByDiaryText(String diaryText) {
        try {
            String result = openAiService.call(
                "music",
                Map.of("text", diaryText)
            );

            JsonNode node = om.readTree(result);
            JsonNode moodNode = node.get("mood");

            if (moodNode == null || !StringUtils.hasText(moodNode.asText())) {
                return null;
            }

            String mood = moodNode.asText().trim();
            List<Music> musicList = musicRepository.findByMood(mood);

            // 해당 mood에 등록된 음악이 없는 경우
            if (musicList.isEmpty()) {
                return null;
            }

            return musicList.get(random.nextInt(musicList.size()));

        } catch (Exception e) {
            // OpenAI 호출 또는 JSON 파싱 실패 시 음악 없이 처리
            return null;
        }
    }


    @Transactional
    public MusicResponse createMusic(MusicCreateRequest request) {
        Music music = Music.builder()
            .videoId(request.getVideoId().trim())
            .title(request.getTitle().trim())
            .artist(request.getArtist().trim())
            .mood(request.getMood().trim())
            .build();

        Music savedMusic = musicRepository.save(music);

        return MusicResponse.from(savedMusic);
    }


    @Transactional
    public MusicResponse updateMusic(
        Long musicId,
        MusicUpdateRequest request
    ) {
        validateUpdateRequest(request);

        Music music = findMusic(musicId);

        if (request.getVideoId() != null) {
            music.setVideoId(
                requireText(request.getVideoId(), "videoId")
            );
        }

        if (request.getTitle() != null) {
            music.setTitle(
                requireText(request.getTitle(), "title")
            );
        }

        if (request.getArtist() != null) {
            music.setArtist(
                requireText(request.getArtist(), "artist")
            );
        }

        if (request.getMood() != null) {
            music.setMood(
                requireText(request.getMood(), "mood")
            );
        }

        // JPA 변경 감지로 UPDATE가 실행되므로 save()는 필요하지 않다.
        return MusicResponse.from(music);
    }


    @Transactional
    public void deleteMusic(Long musicId) {
        Music music = findMusic(musicId);
        musicRepository.delete(music);
    }


    public List<MusicResponse> getMusicsByMood(String mood) {
        String normalizedMood = requireText(mood, "mood");

        return musicRepository.findByMood(normalizedMood)
            .stream()
            .map(MusicResponse::from)
            .toList();
    }


    private Music findMusic(Long musicId) {
        return musicRepository.findById(musicId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "음악을 찾을 수 없습니다. musicId=" + musicId
            ));
    }

    public List<MusicResponse> getAllMusics() {
        return musicRepository.findAll()
            .stream()
            .map(MusicResponse::from)
            .toList();
    }


    private void validateUpdateRequest(MusicUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "요청 본문이 필요합니다."
            );
        }

        boolean hasUpdateField =
            request.getVideoId() != null
                || request.getTitle() != null
                || request.getArtist() != null
                || request.getMood() != null;

        if (!hasUpdateField) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "수정할 값을 하나 이상 입력해야 합니다."
            );
        }
    }


    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                fieldName + "는 빈 값일 수 없습니다."
            );
        }

        return value.trim();
    }
}