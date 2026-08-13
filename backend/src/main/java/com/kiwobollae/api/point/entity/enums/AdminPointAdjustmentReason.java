package com.kiwobollae.api.point.entity.enums;

public enum AdminPointAdjustmentReason {
	SPECIAL_EVENT,
	OUTSTANDING_MEMBER,
	FRAUD_PENALTY;

	public boolean supports(long amount) {
		if (amount > 0) {
			return this == SPECIAL_EVENT || this == OUTSTANDING_MEMBER;
		}
		return amount < 0 && this == FRAUD_PENALTY;
	}
}
