package com.kiwobollae.api.content.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultFfmpegProcessRunner implements FfmpegProcessRunner {

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
		int exitCode = process.waitFor();
		drain.join();
		return exitCode;
	}
}
