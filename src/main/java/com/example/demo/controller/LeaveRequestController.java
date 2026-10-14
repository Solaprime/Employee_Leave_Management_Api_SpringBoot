package com.example.demo.controller;

import com.example.demo.dto.CreateLeaveRequest;
import com.example.demo.dto.DecisionRequest;
import com.example.demo.dto.DecisionResult;
import com.example.demo.dto.LeaveRequestResponse;
import com.example.demo.dto.PageResponse;
import com.example.demo.dto.WorkingDaysResponse;
import com.example.demo.model.LeaveStatus;
import com.example.demo.service.LeaveRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leave-requests")
@Tag(name = "Leave requests")
public class LeaveRequestController {

	/** Set on approve/reject responses when the call repeated a decision that was already recorded. */
	public static final String REPLAYED_HEADER = "Idempotent-Replayed";

	private final LeaveRequestService leaveRequestService;

	public LeaveRequestController(LeaveRequestService leaveRequestService) {
		this.leaveRequestService = leaveRequestService;
	}

	@PostMapping
	@Operation(summary = "Create a leave request for the authenticated employee")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "409", description = "Overlaps an approved leave")
	@ApiResponse(responseCode = "422", description = "No working days in range, or insufficient balance")
	public ResponseEntity<LeaveRequestResponse> create(@Valid @RequestBody CreateLeaveRequest body, Principal principal) {
		LeaveRequestResponse created = leaveRequestService.create(principal.getName(), body);
		return ResponseEntity.created(URI.create("/api/leave-requests/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Retrieve one leave request (owner or manager)")
	public LeaveRequestResponse get(@PathVariable Long id, Principal principal) {
		return leaveRequestService.get(id, principal.getName());
	}

	@GetMapping
	@Operation(summary = "List leave requests",
			description = "Managers see all requests and may filter by employeeId; employees only see their own.")
	public PageResponse<LeaveRequestResponse> list(
			@RequestParam(required = false) Long employeeId,
			@RequestParam(required = false) LeaveStatus status,
			@ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable,
			Principal principal) {
		return leaveRequestService.list(principal.getName(), employeeId, status, pageable);
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("hasRole('MANAGER')")
	@Operation(summary = "Approve a pending leave request (managers)",
			description = "Idempotent: approving an already-approved request returns 200 with the current state "
					+ "and the header Idempotent-Replayed: true; the balance is not deducted again.")
	@ApiResponse(responseCode = "200", description = "Approved (or already approved)")
	@ApiResponse(responseCode = "409", description = "Request is rejected/cancelled, or overlaps another approved leave")
	@ApiResponse(responseCode = "422", description = "Insufficient leave balance")
	public ResponseEntity<LeaveRequestResponse> approve(@PathVariable Long id,
			@Valid @RequestBody(required = false) DecisionRequest body, Principal principal) {
		return toResponse(leaveRequestService.approve(id, principal.getName(), comment(body)));
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasRole('MANAGER')")
	@Operation(summary = "Reject a pending leave request (managers)",
			description = "Idempotent in the same way as approve.")
	public ResponseEntity<LeaveRequestResponse> reject(@PathVariable Long id,
			@Valid @RequestBody(required = false) DecisionRequest body, Principal principal) {
		return toResponse(leaveRequestService.reject(id, principal.getName(), comment(body)));
	}

	@PostMapping("/{id}/cancel")
	@Operation(summary = "Cancel your own pending leave request")
	public LeaveRequestResponse cancel(@PathVariable Long id, Principal principal) {
		return leaveRequestService.cancel(id, principal.getName());
	}

	@GetMapping("/working-days")
	@Operation(summary = "Preview how many leave days a date range would cost",
			description = "Inclusive range; weekends and public holidays are not charged. Fri to next Tue = 3.")
	public WorkingDaysResponse workingDays(
			@Parameter(example = "2026-10-16") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@Parameter(example = "2026-10-20") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return leaveRequestService.previewWorkingDays(startDate, endDate);
	}

	private static String comment(DecisionRequest body) {
		return body == null ? null : body.comment();
	}

	private static ResponseEntity<LeaveRequestResponse> toResponse(DecisionResult result) {
		return ResponseEntity.ok()
				.header(REPLAYED_HEADER, String.valueOf(result.replayed()))
				.body(result.leaveRequest());
	}
}
