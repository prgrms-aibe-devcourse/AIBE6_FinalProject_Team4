package com.kiwobollae.api.point.entity.enums;

public enum PointTxType {
	CHARGE(false),
	JOURNAL_REWARD(false),
	PURCHASE(false),
	RESTORE(false),
	REFUND(false),
	// 관리자 수동 조정: 강제 차감으로 음수까지 내릴 수 있어야 함(POINT-09).
	ADMIN_ADJUST(true);

	private final boolean allowsNegativeFree;

	PointTxType(boolean allowsNegativeFree) {
		this.allowsNegativeFree = allowsNegativeFree;
	}

	/**
	 * free_point를 음수(부채)로 만들 수 있는 유형인지. ADMIN_ADJUST만 true.
	 * 그 외(PURCHASE 등)의 무상 차감은 잔액 하한(0)을 지켜야 하므로 부족 시 거절된다.
	 */
	public boolean allowsNegativeFree() {
		return this.allowsNegativeFree;
	}
}
