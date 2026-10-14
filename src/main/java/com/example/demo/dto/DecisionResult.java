package com.example.demo.dto;

/**
 * Outcome of an approve/reject call. {@code replayed} is true when the request already had the
 * requested status, so nothing was changed (idempotent repeat).
 */
public record DecisionResult(LeaveRequestResponse leaveRequest, boolean replayed) {
}
