package com.momeokji.aiDiarybackend.service;


import com.momeokji.aiDiarybackend.dto.music.MusicResponseDto;
import com.momeokji.aiDiarybackend.entity.Music;
import com.momeokji.aiDiarybackend.repository.MusicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class MusicService {

    private final MusicRepository musicRepository;

    public MusicResponseDto getRandomMusic(String mood){ // Mood에 따른 음악 랜덤으로 가져오기
        List<Music> list = musicRepository.findByMood(mood);
        Music random = list.get(new Random().nextInt(list.size()));

        return new MusicResponseDto(random.getTitle(), random.getArtist(), random.getVideoId());
    }
}
