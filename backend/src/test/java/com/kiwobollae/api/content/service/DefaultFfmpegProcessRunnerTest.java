package com.kiwobollae.api.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DefaultFfmpegProcessRunnerTest {

	@Test
	void runReturnsExitCodeForQuickCommand() throws Exception {
		DefaultFfmpegProcessRunner runner = new DefaultFfmpegProcessRunner(Duration.ofSeconds(5));

		int exitCode = runner.run(quickCommand());

		assertThat(exitCode).isZero();
	}

	@Test
	void runKillsProcessAndThrowsWhenItExceedsTimeout() {
		DefaultFfmpegProcessRunner runner = new DefaultFfmpegProcessRunner(Duration.ofMillis(200));

		assertThatThrownBy(() -> runner.run(hangingCommand()))
				.isInstanceOf(TimelapseEncodingException.class)
				.hasMessageContaining("timed out");
	}

	private List<String> quickCommand() {
		return isWindows() ? List.of("cmd", "/c", "echo hi") : List.of("sh", "-c", "echo hi");
	}

	// 실제 ffmpeg가 멈춘 상황을 흉내내기 위해, 확실히 5초를 넘겨 응답하지 않는 ping을 대신 쓴다.
	private List<String> hangingCommand() {
		return isWindows() ? List.of("ping", "-n", "20", "127.0.0.1") : List.of("ping", "-c", "20", "127.0.0.1");
	}

	private boolean isWindows() {
		return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
	}
}
