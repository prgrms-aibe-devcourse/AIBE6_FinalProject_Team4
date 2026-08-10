package com.kiwobollae.api.timelapse.service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.kiwobollae.api.timelapse.exception.TimelapseEncodingException;
import org.springframework.stereotype.Component;

@Component
public class DefaultFfmpegProcessRunner implements FfmpegProcessRunner {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

	private final Duration timeout;

	public DefaultFfmpegProcessRunner() {
		this(DEFAULT_TIMEOUT);
	}

	DefaultFfmpegProcessRunner(Duration timeout) {
		this.timeout = timeout;
	}

	@Override
	public int run(List<String> command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		// ffmpeg는 진행 로그를 대량으로 stdout/stderr에 쏟아낸다 — 버퍼가 차서 프로세스가
		// 멈추지 않도록 별도 스레드에서 계속 읽어서 버린다.
		Thread drain = new Thread(() -> {
			try {
				process.getInputStream().transferTo(OutputStream.nullOutputStream());
			} catch (IOException ignored) {
				// 프로세스 종료로 스트림이 닫히는 정상 상황도 여기로 온다.
			}
		});
		drain.setDaemon(true);
		drain.start();

		// ffmpeg가 멈춰버리면 waitFor()를 무한정 기다리게 되고, 워커 스레드풀(corePoolSize=1)이
		// 그 한 스레드에서 영원히 막혀버린다 — 그러면 해당 프로필의 PlantTimelapse는 PROCESSING에
		// 갇혀 TIMELAPSE_ALREADY_PROCESSING 때문에 재요청도 불가능해진다. 타임아웃으로 이를 방지한다.
		boolean finishedInTime = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
		if (!finishedInTime) {
			process.destroyForcibly();
			drain.join();
			throw new TimelapseEncodingException(
					"ffmpeg process timed out after " + timeout.toMinutes() + " minutes");
		}

		drain.join();
		return process.exitValue();
	}
}
