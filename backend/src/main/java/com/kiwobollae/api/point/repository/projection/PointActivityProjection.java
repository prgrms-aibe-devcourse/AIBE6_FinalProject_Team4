package com.kiwobollae.api.point.repository.projection;

import java.time.LocalDateTime;

/** 사용자 포인트 내역에서 동일한 거래의 유상·무상 원장을 한 건으로 묶은 조회 projection. */
public interface PointActivityProjection {

	Long getId();

	String getType();

	String getRefType();

	Long getRefId();

	String getAdjustmentReason();

	Long getAmount();

	Long getPaidAmount();

	Long getFreeAmount();

	Long getPaidBalanceAfter();

	Long getFreeBalanceAfter();

	LocalDateTime getCreatedAt();
}
