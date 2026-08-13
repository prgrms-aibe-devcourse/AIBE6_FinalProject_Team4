package com.kiwobollae.api.timelapse.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.timelapse.exception.TimelapseEncodingException;
import org.junit.jupiter.api.Test;

class TimelapseSourceImageTest {

	@Test
	void acceptsAllowedExtensions() {
		assertThatCode(() -> new TimelapseSourceImage("img".getBytes(), ".jpg")).doesNotThrowAnyException();
		assertThatCode(() -> new TimelapseSourceImage("img".getBytes(), ".JPEG")).doesNotThrowAnyException();
		assertThatCode(() -> new TimelapseSourceImage("img".getBytes(), ".png")).doesNotThrowAnyException();
		assertThatCode(() -> new TimelapseSourceImage("img".getBytes(), ".webp")).doesNotThrowAnyException();
	}

	@Test
	void rejectsUnknownExtension() {
		assertThatThrownBy(() -> new TimelapseSourceImage("img".getBytes(), ".exe"))
				.isInstanceOf(TimelapseEncodingException.class);
	}

	// extension은 FfmpegTimelapseEncoder가 만드는 파일명/ffmpeg concat 리스트에 그대로 삽입된다
	// (-safe 0 이라 절대경로도 허용됨). 작은따옴표/개행이 섞이면 concat 리스트에 임의 file 지시문을
	// 주입해 서버 로컬 파일을 읽게 만들 수 있어, 화이트리스트 밖 문자열은 여기서 전부 막는다.
	@Test
	void rejectsExtensionContainingQuoteInjection() {
		assertThatThrownBy(() -> new TimelapseSourceImage("img".getBytes(), ".jpg'\nfile '/etc/passwd"))
				.isInstanceOf(TimelapseEncodingException.class);
	}
}
