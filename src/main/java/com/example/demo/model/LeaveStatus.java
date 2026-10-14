package com.example.demo.model;

/**
 * Lifecycle of a leave request. PENDING is the only non-terminal state:
 * a decision (APPROVED / REJECTED) or a cancellation can only be made from PENDING.
 */
public enum LeaveStatus {
	PENDING,
	APPROVED,
	REJECTED,
	CANCELLED;

	public boolean canTransitionTo(LeaveStatus target) {
		return this == PENDING && target != PENDING;
	}
}
