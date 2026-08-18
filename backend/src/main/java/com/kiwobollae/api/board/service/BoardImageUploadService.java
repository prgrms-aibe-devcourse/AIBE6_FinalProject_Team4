package com.kiwobollae.api.board.service;

import com.kiwobollae.api.board.dto.response.BoardImageUploadResponse;
import com.kiwobollae.api.board.repository.BoardPostImageRepository;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.storage.AbstractS3ImageUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * JournalImageUploadService와 동일한 패턴 — 버킷이 private이라 서버가 대신 스트리밍 서빙한다.
 * 브라우저는 S3와 직접 통신하지 않고, 저장/반환되는 URL은 host-relative다.
 *
 * <p>업로드/서빙/삭제의 S3 공통 로직은 {@link AbstractS3ImageUploadService}에 있고, 이 클래스는
 * "board" 접두사와 게시판 도메인 고유의 삭제 참조 규칙만 담당한다.
 */
@Slf4j
@Service
public class BoardImageUploadService extends AbstractS3ImageUploadService {

	private static final String SERVE_PATH_MARKER = ApiVersion.V1 + "/board/images/";

	private final BoardPostImageRepository boardPostImageRepository;

	public BoardImageUploadService(S3Client s3Client, BoardPostImageRepository boardPostImageRepository) {
		super(s3Client, "board", SERVE_PATH_MARKER, ErrorCode.BOARD_IMAGE_INVALID_TYPE, ErrorCode.BOARD_IMAGE_UPLOAD_FAILED);
		this.boardPostImageRepository = boardPostImageRepository;
	}

	public BoardImageUploadResponse upload(MultipartFile file, Long userId) {
		return new BoardImageUploadResponse(doUpload(file, userId).url());
	}

	/**
	 * 더 이상 어떤 게시글에서도 참조되지 않는 이미지에 대한 best-effort S3 정리. 절대 예외를
	 * 던지지 않는다 — 정리 실패가 이를 유발한 게시글 작성/수정/삭제 자체를 실패시키면 안 된다.
	 */
	public void delete(String imageUrl, Long ownerUserId) {
		String key = keyFromUrl(imageUrl);
		if (key == null) {
			return;
		}
		if (!isOwnedBy(key, ownerUserId)) {
			log.warn("Refused to delete board image not owned by user {}: {}", ownerUserId, key);
			return;
		}
		if (boardPostImageRepository.existsByImageUrl(imageUrl)) {
			log.warn("Refused to delete board image still referenced by a post: {}", key);
			return;
		}
		deleteObject(key);
	}

	public ResponseEntity<byte[]> download(Long userId, String filename) {
		return super.download(userId, filename);
	}
}
