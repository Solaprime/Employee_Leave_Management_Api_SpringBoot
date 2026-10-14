package com.example.demo.service;

import com.example.demo.dto.CreateLeaveRequest;
import com.example.demo.dto.DecisionResult;
import com.example.demo.dto.LeaveRequestResponse;
import com.example.demo.dto.PageResponse;
import com.example.demo.dto.WorkingDaysResponse;
import com.example.demo.entity.Employee;
import com.example.demo.entity.LeaveRequest;
import com.example.demo.exception.BusinessRuleException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ForbiddenException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.LeaveStatus;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.LeaveRequestRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leave request use cases.
 *
 * <h2>Concurrency (Logic 2)</h2>
 * Every write runs in one transaction that first takes a row lock ({@code SELECT ... FOR UPDATE}) on
 * the <em>employee who owns the leave</em>, then on the leave request itself. Locks are always taken in
 * that order, so there is no deadlock. Because the employee row is the shared parent of all of their
 * requests, two managers approving two overlapping requests for the same person are serialised: the
 * second transaction waits, then re-reads committed data, sees the first approval, and fails with 409.
 * The same lock protects the leave balance from being deducted twice.
 * <p>
 * Backstops: {@code @Version} columns turn any write that bypasses the locks into an optimistic-lock
 * failure (409) instead of a lost update, and a CHECK constraint stops the balance going negative.
 *
 * <h2>Idempotent decisions (Logic 3)</h2>
 * Approving an already-approved request (or rejecting an already-rejected one) is a no-op that returns
 * 200 with the current state and {@code replayed = true}. No second deduction, no new timestamp.
 * Trying to flip a final decision (approve a rejected request, etc.) is a 409.
 */
@Service
public class LeaveRequestService {

	private final LeaveRequestRepository leaveRequestRepository;
	private final EmployeeRepository employeeRepository;
	private final WorkingDayCalculator workingDayCalculator;

	public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
			EmployeeRepository employeeRepository,
			WorkingDayCalculator workingDayCalculator) {
		this.leaveRequestRepository = leaveRequestRepository;
		this.employeeRepository = employeeRepository;
		this.workingDayCalculator = workingDayCalculator;
	}

	@Transactional
	public LeaveRequestResponse create(String username, CreateLeaveRequest dto) {
		Employee employee = employeeRepository.findByUsernameForUpdate(username)
				.orElseThrow(() -> new NotFoundException("Employee " + username + " not found"));

		int workingDays = workingDayCalculator.countWorkingDays(dto.startDate(), dto.endDate());
		if (workingDays == 0) {
			throw new BusinessRuleException("The requested period contains no working days");
		}
		if (leaveRequestRepository.existsOverlapping(employee.getId(), LeaveStatus.APPROVED,
				dto.startDate(), dto.endDate(), null)) {
			throw new ConflictException("The requested period overlaps an already approved leave");
		}
		if (workingDays > employee.getLeaveBalance()) {
			throw new BusinessRuleException("Requested %d working days but only %d remain"
					.formatted(workingDays, employee.getLeaveBalance()));
		}

		LeaveRequest saved = leaveRequestRepository.save(
				new LeaveRequest(employee, dto.startDate(), dto.endDate(), workingDays, dto.reason()));
		return LeaveRequestResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public LeaveRequestResponse get(Long id, String username) {
		Employee caller = findEmployee(username);
		LeaveRequest request = leaveRequestRepository.findWithDetailsById(id)
				.orElseThrow(() -> notFound(id));
		if (!caller.isManager() && !request.getEmployee().getId().equals(caller.getId())) {
			throw new ForbiddenException("You can only view your own leave requests");
		}
		return LeaveRequestResponse.from(request);
	}

	/** Managers can list everyone's requests; employees only ever see their own. */
	@Transactional(readOnly = true)
	public PageResponse<LeaveRequestResponse> list(String username, Long employeeId, LeaveStatus status,
			Pageable pageable) {
		Employee caller = findEmployee(username);
		Long effectiveEmployeeId = caller.isManager() ? employeeId : caller.getId();
		return PageResponse.from(leaveRequestRepository.search(effectiveEmployeeId, status, pageable),
				LeaveRequestResponse::from);
	}

	@Transactional
	public DecisionResult approve(Long id, String managerUsername, String comment) {
		LeaveRequest request = lockRequestAndOwner(id);
		Employee owner = request.getEmployee();
		Employee manager = findEmployee(managerUsername);

		if (request.getStatus() == LeaveStatus.APPROVED) {
			return replay(request);
		}
		requireNotSelfDecision(owner, manager);
		if (request.getStatus() != LeaveStatus.PENDING) {
			throw new ConflictException("Leave request %d is already %s and cannot be approved"
					.formatted(id, request.getStatus()));
		}
		if (leaveRequestRepository.existsOverlapping(owner.getId(), LeaveStatus.APPROVED,
				request.getStartDate(), request.getEndDate(), request.getId())) {
			throw new ConflictException("Leave request %d overlaps another approved leave for this employee"
					.formatted(id));
		}
		if (request.getWorkingDays() > owner.getLeaveBalance()) {
			throw new BusinessRuleException("Employee has %d days left; request needs %d"
					.formatted(owner.getLeaveBalance(), request.getWorkingDays()));
		}

		owner.deductLeave(request.getWorkingDays());
		request.approve(manager, comment);
		return new DecisionResult(LeaveRequestResponse.from(request), false);
	}

	@Transactional
	public DecisionResult reject(Long id, String managerUsername, String comment) {
		LeaveRequest request = lockRequestAndOwner(id);
		Employee manager = findEmployee(managerUsername);

		if (request.getStatus() == LeaveStatus.REJECTED) {
			return replay(request);
		}
		requireNotSelfDecision(request.getEmployee(), manager);
		request.reject(manager, comment);
		return new DecisionResult(LeaveRequestResponse.from(request), false);
	}

	/** Owner withdraws a pending request. Repeating the call is harmless. */
	@Transactional
	public LeaveRequestResponse cancel(Long id, String username) {
		LeaveRequest request = lockRequestAndOwner(id);
		if (!request.getEmployee().getUsername().equals(username)) {
			throw new ForbiddenException("You can only cancel your own leave requests");
		}
		if (request.getStatus() != LeaveStatus.CANCELLED) {
			request.cancel();
		}
		return LeaveRequestResponse.from(request);
	}

	@Transactional(readOnly = true)
	public WorkingDaysResponse previewWorkingDays(LocalDate start, LocalDate end) {
		if (end.isBefore(start)) {
			throw new BusinessRuleException("endDate must be on or after startDate");
		}
		int calendarDays = (int) ChronoUnit.DAYS.between(start, end) + 1;
		return new WorkingDaysResponse(start, end, calendarDays, workingDayCalculator.countWorkingDays(start, end));
	}

	/**
	 * Takes the row locks in the fixed order: owner employee first, then the request. The owner id is
	 * fetched with a scalar query so the request entity is first loaded by the locking query and is
	 * therefore guaranteed fresh (not a stale copy from the persistence context).
	 */
	private LeaveRequest lockRequestAndOwner(Long id) {
		Long ownerId = leaveRequestRepository.findEmployeeIdById(id).orElseThrow(() -> notFound(id));
		employeeRepository.findByIdForUpdate(ownerId).orElseThrow(() -> notFound(id));
		return leaveRequestRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
	}

	private static DecisionResult replay(LeaveRequest request) {
		return new DecisionResult(LeaveRequestResponse.from(request), true);
	}

	private static void requireNotSelfDecision(Employee owner, Employee manager) {
		if (owner.getId().equals(manager.getId())) {
			throw new ForbiddenException("Managers cannot decide on their own leave requests");
		}
	}

	private Employee findEmployee(String username) {
		return employeeRepository.findByUsername(username)
				.orElseThrow(() -> new NotFoundException("Employee " + username + " not found"));
	}

	private static NotFoundException notFound(Long id) {
		return new NotFoundException("Leave request " + id + " not found");
	}
}
