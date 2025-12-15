package com.momeokji.aiDiarybackend.dto.redis;


import java.util.List;
import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto.Qna;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReferenceSet {
	private List<Qna> qna;
	private String diary;
	private long createdAt;
}
