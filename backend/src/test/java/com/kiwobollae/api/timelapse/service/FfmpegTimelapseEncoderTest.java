package com.kiwobollae.api.timelapse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.kiwobollae.api.timelapse.exception.TimelapseEncodingException;
import org.junit.jupiter.api.Test;

class FfmpegTimelapseEncoderTest {

	@Test
	void encodeWritesImagesAndReturnsRunnerOutput() throws IOException {
		byte[] expectedVideoBytes = "fake-mp4-bytes".getBytes();
		// 마지막 인자가 출력 파일 경로라는 컨벤션(FfmpegTimelapseEncoder가 만든 명령어)을 이용해,
		// 실제 ffmpeg 없이도 "인코딩이 성공하면 출력 파일이 생긴다"를 흉내낸다.
		FfmpegProcessRunner fakeRunner = command -> {
			String outputPath = command.get(command.size() - 1);
			Files.write(Path.of(outputPath), expectedVideoBytes);
			return 0;
		};
		FfmpegTimelapseEncoder encoder = new FfmpegTimelapseEncoder(fakeRunner);
		List<TimelapseSourceImage> images = List.of(
				new TimelapseSourceImage("img1".getBytes(), ".jpg"),
				new TimelapseSourceImage("img2".getBytes(), ".png"));

		byte[] result = encoder.encode(images);

		assertThat(result).isEqualTo(expectedVideoBytes);
	}

	@Test
	void encodeThrowsWhenRunnerReturnsNonZeroExitCode() {
		FfmpegProcessRunner failingRunner = command -> 1;
		FfmpegTimelapseEncoder encoder = new FfmpegTimelapseEncoder(failingRunner);
		List<TimelapseSourceImage> images = List.of(
				new TimelapseSourceImage("img1".getBytes(), ".jpg"),
				new TimelapseSourceImage("img2".getBytes(), ".jpg"));

		assertThatThrownBy(() -> encoder.encode(images)).isInstanceOf(TimelapseEncodingException.class);
	}
}
