package com.momeokji.aiDiarybackend.entity;


import jakarta.persistence.*;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "music")
public class Music {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    @Column(name = "title")
    private String title;

    @Column(name = "artist")
    private String artist;

    @Column(name = "mood")
    private String mood;

    /** 재생 시간(초). 상세 화면의 "3:02" 에 쓴다. 모르면 null. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;


}
