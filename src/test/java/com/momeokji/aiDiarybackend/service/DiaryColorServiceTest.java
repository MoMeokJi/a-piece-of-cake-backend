package com.momeokji.aiDiarybackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.momeokji.aiDiarybackend.service.DiaryColorService.ColorPick;

/** OpenAI 색상 응답 파서. 새 형식(이름+hex 객체)과 구형(hex 문자열)을 모두 받는다. */
class DiaryColorServiceTest {

	DiaryColorService service = new DiaryColorService();

	@Test
	void objectFormatGivesNameAndHex() {
		List<ColorPick> out = service.parseColors(
			"```json\n{\"colors\":[{\"name\":\"새싹 연두\",\"hex\":\"#c3e9a6\"},{\"name\":\"하늘\",\"hex\":\"#AABBCC\"}]}\n```");

		assertEquals(List.of(new ColorPick("새싹 연두", "#C3E9A6"), new ColorPick("하늘", "#AABBCC")), out);
	}

	@Test
	void legacyStringFormatGivesNullNames() {
		List<ColorPick> out = service.parseColors("{\"colors\":\"#AABBCC, #DDEEFF\"}");

		assertEquals(List.of(new ColorPick(null, "#AABBCC"), new ColorPick(null, "#DDEEFF")), out);
	}

	@Test
	void bareTextGivesNullNames() {
		assertEquals(List.of(new ColorPick(null, "#112233")), service.parseColors("색은 #112233 입니다"));
	}

	@Test
	void objectWithoutValidHexIsSkipped() {
		List<ColorPick> out = service.parseColors(
			"{\"colors\":[{\"name\":\"x\",\"hex\":\"red\"},{\"name\":\"y\",\"hex\":\"#000000\"}]}");

		assertEquals(List.of(new ColorPick("y", "#000000")), out);
	}

	@Test
	void noHexThrows() {
		assertThrows(IllegalStateException.class, () -> service.parseColors("없음"));
	}

	/** 이름은 LLM 이 주는 값이라 길이를 보장할 수 없다. DB 컬럼(30자)에 맞춰 잘라 저장 실패로 일기를 잃지 않게 한다. */
	@Test
	void longNameIsTruncatedToThirtyChars() {
		String longName = "아주".repeat(20); // 40자
		List<ColorPick> out = service.parseColors("{\"colors\":[{\"name\":\"" + longName + "\",\"hex\":\"#000000\"}]}");

		assertEquals("아주".repeat(15), out.get(0).name());
		assertEquals(30, out.get(0).name().length());
	}
}
