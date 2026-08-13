package com.kiwobollae.api.board.entity.enums;

// 게시글 목록 검색 시 키워드를 어느 필드에 매칭할지 지정한다. keyword가 없으면 무시된다.
public enum BoardSearchType {
	TITLE_CONTENT,
	TITLE,
	CONTENT,
	AUTHOR,
	COMMENT
}
