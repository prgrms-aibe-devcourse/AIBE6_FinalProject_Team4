package com.kiwobollae.api.board.repository;

import com.kiwobollae.api.board.entity.BoardPostView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardPostViewRepository extends JpaRepository<BoardPostView, Long> {

	boolean existsByPostIdAndIpAddress(Long postId, String ipAddress);
}
