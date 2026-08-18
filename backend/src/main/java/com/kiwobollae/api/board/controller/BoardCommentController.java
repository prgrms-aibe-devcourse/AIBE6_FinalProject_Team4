package com.kiwobollae.api.board.controller;

import com.kiwobollae.api.board.dto.request.BoardCommentCreateRequest;
import com.kiwobollae.api.board.dto.response.BoardCommentResponse;
import com.kiwobollae.api.board.service.BoardCommentService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "게시판 댓글", description = "게시판 댓글 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/board/posts/{postId}/comments")
public class BoardCommentController {

	private final BoardCommentService boardCommentService;

	@Operation(summary = "댓글 작성", description = "게시글에 댓글을 생성합니다.")
	@PostMapping
	public ResponseEntity<ApiResponse<BoardCommentResponse>> createComment(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long postId,
			@Valid @RequestBody BoardCommentCreateRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(boardCommentService.createComment(userId, postId, request)));
	}

	@Operation(summary = "댓글 목록 조회", description = "게시글의 댓글을 작성 시간순으로 반환합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<List<BoardCommentResponse>>> getComments(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long postId
	) {
		return ResponseEntity.ok(ApiResponse.success(boardCommentService.getComments(postId, userId)));
	}
}
