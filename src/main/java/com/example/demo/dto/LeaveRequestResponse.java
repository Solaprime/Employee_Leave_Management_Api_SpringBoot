package com.example.demo.dto;

import com.example.demo.entity.Employee;
import com.example.demo.entity.LeaveRequest;
import com.example.demo.model.LeaveStatus;
import java.time.Instant;
import java.time.LocalDate;

public record LeaveRequestResponse(
		Long id,
		Long employeeId,
		String employeeName,
		LocalDate startDate,
		LocalDate endDate,
		int workingDays,
		String reason,
		LeaveStatus status,
		Long decidedById,
		String decidedByName,
		Instant decidedAt,
		String decisionComment,
		Instant createdAt) {

	public static LeaveRequestResponse from(LeaveRequest r) {
		Employee decidedBy = r.getDecidedBy();
		return new LeaveRequestResponse(
				r.getId(),
				r.getEmployee().getId(),
				r.getEmployee().getFullName(),
				r.getStartDate(),
				r.getEndDate(),
				r.getWorkingDays(),
				r.getReason(),
				r.getStatus(),
				decidedBy == null ? null : decidedBy.getId(),
				decidedBy == null ? null : decidedBy.getFullName(),
				r.getDecidedAt(),
				r.getDecisionComment(),
				r.getCreatedAt());
	}
}
