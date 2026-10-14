package com.example.demo.entity;

import com.example.demo.exception.ConflictException;
import com.example.demo.model.LeaveStatus;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "leave_requests",
		indexes = @Index(name = "ix_leave_requests_employee_status", columnList = "employee_id, status"),
		check = {
				@CheckConstraint(name = "ck_leave_requests_date_range", constraint = "end_date >= start_date"),
				@CheckConstraint(name = "ck_leave_requests_working_days", constraint = "working_days > 0")
		})
public class LeaveRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employee_id", nullable = false)
	private Employee employee;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "working_days", nullable = false)
	private int workingDays;

	@Column(length = 500)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LeaveStatus status = LeaveStatus.PENDING;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by_id")
	private Employee decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "decision_comment", length = 500)
	private String decisionComment;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Optimistic-locking version: any concurrent write that slips past the row locks fails instead of overwriting. */
	@Version
	private Long version;

	protected LeaveRequest() {
	}

	public LeaveRequest(Employee employee, LocalDate startDate, LocalDate endDate, int workingDays, String reason) {
		this.employee = employee;
		this.startDate = startDate;
		this.endDate = endDate;
		this.workingDays = workingDays;
		this.reason = reason;
		this.createdAt = now();
	}

	public void approve(Employee manager, String comment) {
		decide(LeaveStatus.APPROVED, manager, comment);
	}

	public void reject(Employee manager, String comment) {
		decide(LeaveStatus.REJECTED, manager, comment);
	}

	public void cancel() {
		transitionTo(LeaveStatus.CANCELLED);
	}

	private void decide(LeaveStatus target, Employee manager, String comment) {
		transitionTo(target);
		this.decidedBy = manager;
		this.decidedAt = now();
		this.decisionComment = comment;
	}

	private void transitionTo(LeaveStatus target) {
		if (!status.canTransitionTo(target)) {
			throw new ConflictException("Leave request %d is %s and cannot be moved to %s".formatted(id, status, target));
		}
		this.status = target;
	}

	/** Truncated to the database column precision so in-memory and reloaded values are identical. */
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public Long getId() {
		return id;
	}

	public Employee getEmployee() {
		return employee;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public int getWorkingDays() {
		return workingDays;
	}

	public String getReason() {
		return reason;
	}

	public LeaveStatus getStatus() {
		return status;
	}

	public Employee getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public String getDecisionComment() {
		return decisionComment;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Long getVersion() {
		return version;
	}
}
