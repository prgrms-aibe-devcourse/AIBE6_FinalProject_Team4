package com.kiwobollae.api.board.controller;

import com.kiwobollae.api.board.dto.request.BoardPostCreateRequest;
import com.kiwobollae.api.board.dto.request.BoardPostUpdateRequest;
import com.kiwobollae.api.board.dto.response.BoardPostResponse;
import com.kiwobollae.api.board.entity.enums.BoardCategory;
import com.kiwobollae.api.board.entity.enums.BoardSearchType;
import com.kiwobollae.api.board.service.BoardPostService;
import com.kiwobollae.api.journal.dto.response.PlantJournalResponse;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "게시판", description = "커뮤니티 게시판(공지사항/자유게시판/식물 Q&A) 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/board/posts")
public class BoardController {

	private final BoardPostService boardPostService;

	@Operation(
			summary = "게시글 작성",
			description = "카테고리, 제목, 본문으로 게시글을 생성합니다. NOTICE는 관리자만, PLANT_QNA는 본인 소유 일지 연동이 필수입니다."
	)
	@PostMapping
	public ResponseEntity<ApiResponse<BoardPostResponse>> createPost(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody BoardPostCreateRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(boardPostService.createPost(userId, request)));
	}

	@Operation(
			summary = "게시글 목록 조회",
			description = "카테고리 필터, 키워드 검색(searchType: TITLE_CONTENT(기본)/TITLE/CONTENT/AUTHOR/COMMENT), "
					+ "페이지네이션, 정렬(최신 기본)을 지원합니다."
	)
	@GetMapping
	public ResponseEntity<ApiResponse<Page<BoardPostResponse>>> getPosts(
			@AuthenticationPrincipal Long userId,
			@RequestParam(required = false) BoardCategory category,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) BoardSearchType searchType,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable
	) {
		return ResponseEntity.ok(
				ApiResponse.success(boardPostService.getPosts(category, keyword, searchType, pageable, userId)));
	}

	@Operation(summary = "게시글 상세 조회", description = "게시글 본문과 작성자 정보를 반환합니다. 조회수는 같은 IP당 1회만 증가합니다.")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<BoardPostResponse>> getPost(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			HttpServletRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(boardPostService.getPost(id, userId, resolveClientIp(request))));
	}

	// 이 앱 앞에는 리버스 프록시(NPM/nginx)가 정확히 한 홉만 있고, nginx는 X-Forwarded-For에
	// 자신이 실제로 관측한 접속 IP를 매번 "끝에" 추가한다(append 방식). 그런데 이 헤더는
	// 클라이언트가 요청에 직접 실어 보낼 수도 있는 값이라, 맨 앞(첫 번째) 항목을 그대로 신뢰하면
	// 클라이언트가 자기 요청에 X-Forwarded-For: 1.2.3.4를 미리 넣어 보내는 것만으로 IP를 위조해
	// 동일 IP당 조회수 1회 제한이나 게시글/댓글 rate limit을 우회할 수 있다. 신뢰할 수 있는 건
	// 프록시가 직접 추가한 마지막 항목뿐이므로 그 값만 쓴다.
	// RateLimitFilter의 동일 로직과 중복이지만, 필터는 컨트롤러에서 재사용할 수 있는 형태가
	// 아니라 이 한 곳에서만 쓰는 지금은 그대로 각자 두고 필요해지면 공용 유틸로 뺀다.
	private String resolveClientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			String[] hops = forwardedFor.split(",");
			return hops[hops.length - 1].trim();
		}
		return request.getRemoteAddr();
	}

	@Operation(
			summary = "게시글에 연동된 성장 일지 조회",
			description = "PLANT_QNA 게시글에 연동된 일지를 작성자가 아닌 다른 사용자도 볼 수 있습니다."
	)
	@GetMapping("/{id}/journal")
	public ResponseEntity<ApiResponse<PlantJournalResponse>> getLinkedJournal(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(boardPostService.getLinkedJournal(id)));
	}

	@Operation(summary = "게시글 수정", description = "작성자 본인만 제목/본문을 수정할 수 있습니다. 카테고리는 변경할 수 없습니다.")
	@PatchMapping("/{id}")
	public ResponseEntity<ApiResponse<BoardPostResponse>> updatePost(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			@Valid @RequestBody BoardPostUpdateRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(boardPostService.updatePost(userId, id, request)));
	}

	@Operation(summary = "게시글 삭제", description = "작성자 본인만 삭제할 수 있습니다. 물리 삭제 없이 숨김 처리합니다.")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deletePost(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
		boardPostService.deletePost(userId, id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "게시글 좋아요", description = "사용자당 게시글 1회 좋아요만 허용합니다.")
	@PostMapping("/{id}/likes")
	public ResponseEntity<Void> likePost(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
		boardPostService.likePost(userId, id);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@Operation(summary = "게시글 좋아요 취소", description = "이미 누른 좋아요를 취소합니다.")
	@DeleteMapping("/{id}/likes")
	public ResponseEntity<Void> unlikePost(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
		boardPostService.unlikePost(userId, id);
		return ResponseEntity.noContent().build();
	}
}
