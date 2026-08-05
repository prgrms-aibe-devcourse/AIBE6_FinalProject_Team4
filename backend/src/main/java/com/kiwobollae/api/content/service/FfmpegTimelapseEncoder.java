package com.kiwobollae.api.content.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 대표이미지들을 0.5초/장, 1:1 비율(720x720) 레터박스로 합성해 mp4 바이트를 만든다. */
@Component
@RequiredArgsConstructor
public class FfmpegTimelapseEncoder {

	private static final double SECONDS_PER_IMAGE = 0.5;
	private static final int SIZE = 720;

	private final FfmpegProcessRunner processRunner;

	public byte[] encode(List<TimelapseSourceImage> images) {
		Path workDir = null;
		try {
			workDir = Files.createTempDirectory("timelapse-");
			List<Path> imagePaths = writeImages(workDir, images);
			Path listFile = writeConcatList(workDir, imagePaths);
			Path output = workDir.resolve("output.mp4");
			List<String> command = buildCommand(listFile, output);

			int exitCode = processRunner.run(command);
			if (exitCode != 0 || !Files.exists(output)) {
				throw new TimelapseEncodingException("ffmpeg exited with code " + exitCode);
			}
			return Files.readAllBytes(output);
		} catch (IOException e) {
			throw new TimelapseEncodingException("Failed to encode timelapse video", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TimelapseEncodingException("Timelapse encoding was interrupted", e);
		} finally {
			if (workDir != null) {
				deleteRecursively(workDir);
			}
		}
	}

	private List<String> buildCommand(Path listFile, Path output) {
		String scalePad = "scale=" + SIZE + ":" + SIZE + ":force_original_aspect_ratio=decrease,"
				+ "pad=" + SIZE + ":" + SIZE + ":(ow-iw)/2:(oh-ih)/2:black";
		return List.of(
				"ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", listFile.toString(),
				"-vf", scalePad,
				"-r", "30", "-pix_fmt", "yuv420p", output.toString());
	}

	private List<Path> writeImages(Path workDir, List<TimelapseSourceImage> images) throws IOException {
		List<Path> paths = new ArrayList<>();
		for (int i = 0; i < images.size(); i++) {
			TimelapseSourceImage image = images.get(i);
			Path path = workDir.resolve("img" + i + image.extension());
			Files.write(path, image.content());
			paths.add(path);
		}
		return paths;
	}

	private Path writeConcatList(Path workDir, List<Path> imagePaths) throws IOException {
		Path listFile = workDir.resolve("list.txt");
		StringBuilder sb = new StringBuilder();
		for (Path imagePath : imagePaths) {
			sb.append("file '").append(imagePath.toAbsolutePath()).append("'\n");
			sb.append("duration ").append(SECONDS_PER_IMAGE).append('\n');
		}
		// concat demuxer 관례: 마지막 파일을 한 번 더 반복해야 마지막 이미지도 duration만큼 보인다.
		if (!imagePaths.isEmpty()) {
			sb.append("file '").append(imagePaths.get(imagePaths.size() - 1).toAbsolutePath()).append("'\n");
		}
		Files.writeString(listFile, sb.toString());
		return listFile;
	}

	private void deleteRecursively(Path dir) {
		try (var stream = Files.walk(dir)) {
			stream.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// best-effort 정리 — 임시 디렉토리 삭제 실패가 인코딩 결과에 영향을 주지 않는다.
				}
			});
		} catch (IOException ignored) {
			// workDir 자체가 없거나 접근 불가한 경우도 무시 — best-effort.
		}
	}
}
