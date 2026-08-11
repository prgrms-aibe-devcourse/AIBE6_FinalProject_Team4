package com.kiwobollae.api.timelapse.service;

import com.kiwobollae.api.timelapse.exception.TimelapseEncodingException;

import java.util.Locale;
import java.util.Set;

/**
 * extension은 FfmpegTimelapseEncoder가 임시 파일명과 ffmpeg concat 리스트(-safe 0, 절대경로 허용)에
 * 그대로 삽입한다 — 따옴표/개행이 섞인 값이 들어오면 concat 리스트에 임의 file 지시문을 주입해 서버
 * 로컬 파일을 인코딩 결과로 유출시킬 수 있다. 업로드 시점에 이미 검증된 확장자(JournalImageUploadService.
 * ALLOWED_EXTENSIONS)만 허용해 이 생성 시점에서 원천 차단한다.
 */
public record TimelapseSourceImage(byte[] content, String extension) {

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

	public TimelapseSourceImage {
		if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
			throw new TimelapseEncodingException("Unsupported timelapse source image extension: " + extension);
		}
	}
}
