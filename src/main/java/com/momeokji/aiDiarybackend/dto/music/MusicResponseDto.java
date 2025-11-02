package com.momeokji.aiDiarybackend.dto.music;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class MusicResponseDto {
    private String title;
    private String artist;
    private String videoId;
}
