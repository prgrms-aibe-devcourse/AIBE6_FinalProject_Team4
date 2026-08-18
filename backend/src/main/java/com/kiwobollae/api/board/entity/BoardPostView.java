package com.kiwobollae.api.board.entity;

import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 같은 IP가 같은 게시글을 여러 번 조회해도 조회수를 한 번만 올리기 위한 기록. 로그인 여부와
// 무관하게(비로그인 열람도 포함) IP 기준으로만 중복을 막는다.
@Getter
@Entity
@Table(name = "board_post_views",
		uniqueConstraints = @UniqueConstraint(name = "uk_board_post_views_post_id_ip", columnNames = {"post_id", "ip_address"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardPostView extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id", nullable = false)
	private BoardPost post;

	@Column(name = "ip_address", nullable = false, length = 45)
	private String ipAddress;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	public static BoardPostView create(BoardPost post, String ipAddress, LocalDateTime createdAt) {
		return BoardPostView.builder()
				.post(post)
				.ipAddress(ipAddress)
				.createdAt(createdAt)
				.build();
	}
}
