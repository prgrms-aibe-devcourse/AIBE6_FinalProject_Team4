package com.kiwobollae.api.content.service;

import java.io.IOException;
import java.util.List;

/** ffmpeg 실행을 감싸는 인터페이스 — 단위 테스트에서 실제 바이너리 없이 가짜 구현으로 교체하기 위함. */
public interface FfmpegProcessRunner {

	int run(List<String> command) throws IOException, InterruptedException;
}
