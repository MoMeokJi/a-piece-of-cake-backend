package com.momeokji.aiDiarybackend.service;


import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.momeokji.aiDiarybackend.dto.music.MusicResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class YoutubeService {

    private final MusicService musicService;
    private final YouTube youtube;

    @Value("${youtube.api.key}")
    private String youtubeApiKey;

    public MusicResponseDto findVideoId(String title, String artist, String mood) { // Youtube videoId 탐색
        try {


            YouTube.Search.List search = youtube.search().list("id,snippet");
            search.setQ(title + " " + artist + " official");
            search.setKey(youtubeApiKey);
            search.setType("video");
            search.setMaxResults(1L);
            search.setType("video");
            search.setVideoCategoryId("10"); // 음악 카테고리

            SearchListResponse response = search.execute();

            if (!response.getItems().isEmpty()) {
                var item = response.getItems().get(0);
                String videoId = item.getId().getVideoId();

                musicService.saveMusic(title, artist, videoId, mood);
                                                                        // Mood로 DB에서 음악을 가져올 때와 형식이 같아야하므로
                return new  MusicResponseDto(title, artist, videoId);   // title, artist, videoID 모두 반환
            } else {
                return musicService.getRandomMusic(mood);   // 모든 호출 실패 상황에서 RandomMusic 반환

            }
        } catch (GoogleJsonResponseException e) {
            log.error("GoogleJsonResponseException: {}", e.getDetails().getMessage());
            return musicService.getRandomMusic(mood);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            return musicService.getRandomMusic(mood);
        }
    }




}
