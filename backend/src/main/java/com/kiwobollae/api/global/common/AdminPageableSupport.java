package com.kiwobollae.api.global.common;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

/**
 * 관리자 콘솔의 "전체 목록" 화면은 클라이언트 쪽에서 전체 데이터를 한 번에 받아 커스텀 정렬·묶음
 * 처리를 한 뒤 페이지네이션한다. 전역 spring.data.web.pageable.max-page-size(공개 API 남용 방지용)에
 * 걸리면 100건 넘는 데이터에서 뒷페이지가 통째로 비게 되므로, ADMIN 권한으로 잠긴 이 엔드포인트들만
 * 그 상한을 벗어난 size를 받아 별도의(더 큰) 상한으로 재구성한다.
 */
public final class AdminPageableSupport {

	private static final int ADMIN_MAX_PAGE_SIZE = 2000;

	private AdminPageableSupport() {
	}

	public static Pageable withUncappedSize(Integer requestedSize, Pageable pageable) {
		if (requestedSize == null) {
			return pageable;
		}
		int boundedSize = Math.min(Math.max(requestedSize, 1), ADMIN_MAX_PAGE_SIZE);
		return PageRequest.of(pageable.getPageNumber(), boundedSize, pageable.getSort());
	}
}
