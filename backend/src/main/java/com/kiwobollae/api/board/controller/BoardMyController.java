package com.kiwobollae.api.board.controller;

import com.kiwobollae.api.board.dto.response.BoardCommentResponse;
import com.kiwobollae.api.board.dto.response.BoardPostResponse;
import com.kiwobollae.api.board.service.BoardCommentService;
import com.kiwobollae.api.board.service.BoardPostService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이페이지 게시판", description = "내가 쓴 게시글/댓글 모아보기")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/my/board")
public class BoardMyController {

	private final BoardPostService boardPostService;
	private final BoardCommentService boardCommentService;

	@Operation(summary = "내가 쓴 게시글 목록", description = "숨김 처리된 게시글도 포함해 작성 시간순으로 반환합니다.")
	@GetMapping("/posts")
	public ResponseEntity<ApiResponse<Page<BoardPostResponse>>> getMyPosts(
			@AuthenticationPrincipal Long userId,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable
	) {
		return ResponseEntity.ok(ApiResponse.success(boardPostService.getMyPosts(userId, pageable)));
	}

	@Operation(summary = "내가 쓴 댓글 목록", description = "숨김 처리된 댓글도 포함해 작성 시간순으로 반환합니다.")
	@GetMapping("/comments")
	public ResponseEntity<ApiResponse<Page<BoardCommentResponse>>> getMyComments(
			@AuthenticationPrincipal Long userId,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable
	) {
		return ResponseEntity.ok(ApiResponse.success(boardCommentService.getMyComments(userId, pageable)));
	}
}
